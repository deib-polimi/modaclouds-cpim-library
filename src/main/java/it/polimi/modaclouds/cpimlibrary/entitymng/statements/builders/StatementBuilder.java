/**
 * Copyright 2013 deib-polimi
 * Contact: deib-polimi <marco.miglierina@polimi.it>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders;

import it.polimi.modaclouds.cpimlibrary.entitymng.PersistenceMetadata;
import it.polimi.modaclouds.cpimlibrary.entitymng.ReflectionUtils;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.InsertStatement;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.Statement;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.Lexer;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.Token;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.TokenType;
import it.polimi.modaclouds.cpimlibrary.exception.PersistenceMetadataException;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.CascadeType;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Query;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Contains abstract algorithms to build statements fro queries or objects.
 *
 * @author Fabio Arcidiacono.
 */
@Slf4j
public abstract class StatementBuilder {

    private boolean followCascades;
    private List<CascadeType> relevantCascadeTypes;

    /**
     * Instantiate a builder that does not follow cascade types.
     *
     * @see javax.persistence.CascadeType
     */
    public StatementBuilder() {
        this.followCascades = false;
        this.relevantCascadeTypes = null;
    }

    /**
     * Instruct the builder to follow cascade types.
     *
     * @param relevantCascadeTypes a list of cascade types that need to be handled
     *
     * @see javax.persistence.CascadeType
     */
    public void followCascades(List<CascadeType> relevantCascadeTypes) {
        this.followCascades = true;
        this.relevantCascadeTypes = relevantCascadeTypes;
    }

    /*
     * In case a InsertStatement is added it must be first, before its dependencies,
     * since entity must exists before referencing it from join table.
     *
     * For Update or Delete statements, vice versa, the join table must be handled first.
     */
    private void addStatementToStack(Deque<Statement> stack, Statement statement) {
        if (statement instanceof InsertStatement) {
            stack.addFirst(statement);
        } else {
            stack.addLast(statement);
        }
    }

    /*
     * In adding a join table statement no particular order is required among them.
     */
    private void addJoinTableStatementToStack(Deque<Statement> stack, Statement statement) {
        stack.addFirst(statement);
    }

    /*
     * While adding statement generated by cascade, maintain the order of generated cascaded statements
     * and add them first in current stack
     */
    private void addCascadedStatementsToStack(Deque<Statement> stack, Deque<Statement> cascadedStack) {
        Iterator<Statement> reverseItr = cascadedStack.descendingIterator();
        while (reverseItr.hasNext()) {
            stack.addFirst(reverseItr.next());
        }
    }

    /*---------------------------------------------------------------------------------*/
    /*----------------------------- BUILD FROM OBJECT ---------------------------------*/
    /*---------------------------------------------------------------------------------*/

    /**
     * Main abstract algorithm that build statements from object. Follows template pattern.
     * Abstract methods are implemented in sub classes, is possible to modify the standard behavior
     * of the algorithm overriding the hook methods (the protected ones).
     *
     * @param entity the object from which build statements
     *
     * @return a {@link java.util.Deque} used as stack containing the statements build from the given entity
     */
    public Deque<Statement> build(Object entity) {
        Deque<Statement> stack = new ArrayDeque<>();
        Deque<Statement> cascadedStack = new ArrayDeque<>();
        Statement statement = initStatement();
        setTableName(statement, entity);

        Field[] fields = getFields(entity);
        for (Field field : fields) {
            if (ReflectionUtils.isRelational(field)) {
                log.debug("{} is a relational field", field.getName());
                if (ReflectionUtils.ownRelation(field)) {
                    log.debug("{} is the owning side of the relation", field.getName());
                    if (followCascades) {
                        cascadedStack = handleCascade(entity, field);
                    } else {
                        log.info("Ignore cascade on field {}", field.getName());
                    }
                    if (ReflectionUtils.isFieldAnnotatedWith(field, ManyToMany.class)) {
                        log.debug("{} holds a ManyToMany relationship, handle JoinTable", field.getName());
                        handleJoinTable(stack, entity, field);
                    } else {
                        onRelationalField(statement, entity, field);
                    }
                } else {
                    log.debug("{} is the non-owning side of the relation, ignore it", field.getName());
                    if (ReflectionUtils.isFieldAnnotatedWith(field, ManyToMany.class)) {
                        log.debug("{} holds a inverse ManyToMany relationship, handle JoinTable", field.getName());
                        handleInverseJoinTable(stack, entity, field);
                    }
                }
            } else if (ReflectionUtils.isId(field)) {
                onIdField(statement, entity, field);
            } else {
                onFiled(statement, entity, field);
            }
        }

        addStatementToStack(stack, statement);
        if (!cascadedStack.isEmpty()) {
            addCascadedStatementsToStack(stack, cascadedStack);
        }
        return stack;
    }

    /**
     * Generate specific instance of statement.
     *
     * @return statement instance
     *
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.statements.Statement
     */
    protected abstract Statement initStatement();

    /**
     * Generate a join table statement from the owning side point of view.
     *
     * @param entity    the entity owning the relationship
     * @param element   an element from the non-owning side of the relationship
     * @param joinTable the join table annotation
     *
     * @return the generated join table statement
     */
    protected abstract Statement generateJoinTableStatement(Object entity, Object element, JoinTable joinTable);

    /**
     * Generate a join table statement from the non-owning side point of view.
     *
     * @param entity    the entity owning the non-owning side the relationship
     * @param joinTable the join table annotation
     *
     * @return the generated join table statement
     */
    protected abstract Statement generateInverseJoinTableStatement(Object entity, JoinTable joinTable);

    /**
     * Hook to handle the processing of Id field.
     *
     * @param statement injected statement
     * @param entity    the entity
     * @param idFiled   the filed of the entity maintaining the id
     */
    protected abstract void onIdField(Statement statement, Object entity, Field idFiled);

    /**
     * Hook to handle the processing of a field.
     *
     * @param statement injected statement
     * @param entity    the entity
     * @param field     the filed to process
     */
    protected abstract void onFiled(Statement statement, Object entity, Field field);

    /**
     * Hook to handle the processing of a field that maintains a relationship with another entity.
     *
     * @param statement injected statement
     * @param entity    the entity
     * @param field     the filed maintaining a relationship
     */
    protected abstract void onRelationalField(Statement statement, Object entity, Field field);

    /**
     * Look for table name inside the entity and modifies the injected statements accordingly.
     *
     * @param statement injected statement to modify
     * @param entity    entity to be parsed
     */
    protected void setTableName(Statement statement, Object entity) {
        String tableName = ReflectionUtils.getJPATableName(entity);
        log.debug("Class {} have {} as JPA table name", entity.getClass().getSimpleName(), tableName);
        statement.setTable(tableName);
    }

    /**
     * Hook to the way the fields are retrieved from the entity.
     *
     * @param entity entity to be parsed
     *
     * @return an array of entity fields
     */
    protected Field[] getFields(Object entity) {
        return ReflectionUtils.getFields(entity);
    }

    /**
     * Gets cascade types declared on field and if necessary call a statement build on related entities.
     *
     * @param entity entity to be parsed
     * @param field  a relational field
     */
    protected Deque<Statement> handleCascade(Object entity, Field field) {
        Deque<Statement> cascadedStack = new ArrayDeque<>();
        CascadeType[] declaredCascadeTypes = ReflectionUtils.getCascadeTypes(field);
        for (CascadeType cascadeType : declaredCascadeTypes) {
            if (this.relevantCascadeTypes.contains(cascadeType)) {
                Object cascadeEntity = ReflectionUtils.getFieldValue(entity, field);
                if (cascadeEntity instanceof Collection) {
                    for (Object cascade : (Collection) cascadeEntity) {
                        log.warn("Cascade operation on collection field {} with value {}", field.getName(), cascade);
                        cascadedStack.addAll(build(cascade));
                    }
                } else {
                    log.warn("Cascade operation on field {} with value {}", field.getName(), cascadeEntity);
                    cascadedStack.addAll(build(cascadeEntity));
                }
            }
        }
        return cascadedStack;
    }

    /**
     * From the owning side of the relationship, iterate through collection of related entities
     * and for each one calls {@link #generateJoinTableStatement(Object, Object, javax.persistence.JoinTable)}.
     *
     * @param stack  stack in witch insert the generated statements
     * @param entity entity to be parsed
     * @param field  the field owning the {@link javax.persistence.ManyToMany} relationship
     */
    protected void handleJoinTable(Deque<Statement> stack, Object entity, Field field) {
        JoinTable joinTable = ReflectionUtils.getAnnotation(field, JoinTable.class);

        Collection collection = (Collection) ReflectionUtils.getFieldValue(entity, field);
        for (Object element : collection) {
            Statement statement = generateJoinTableStatement(entity, element, joinTable);
            if (statement != null) {
                log.debug("joinTableStatement: {}", statement.toString());
                addJoinTableStatementToStack(stack, statement);
            }
        }
    }

    /**
     * From the non-owning side of the relationship, prepare data to call
     * {@link #generateInverseJoinTableStatement(Object, javax.persistence.JoinTable)}.
     *
     * @param stack  stack in witch insert the generated statements
     * @param entity entity to be parsed
     * @param field  the field owning the inverse side of the {@link javax.persistence.ManyToMany} relationship
     */
    protected void handleInverseJoinTable(Deque<Statement> stack, Object entity, Field field) {
        ManyToMany mtm = ReflectionUtils.getAnnotation(field, ManyToMany.class);
        String mappedBy = mtm.mappedBy();
        ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
        Class<?> ownerClass = (Class<?>) collectionType.getActualTypeArguments()[0];
        log.debug("{} is mapped by {} in class {}", field.getName(), mappedBy, ownerClass.getCanonicalName());

        Field ownerField = ReflectionUtils.getFieldByName(ownerClass, mappedBy);
        JoinTable joinTable = ReflectionUtils.getAnnotation(ownerField, JoinTable.class);
        Statement statement = generateInverseJoinTableStatement(entity, joinTable);
        if (statement != null) {
            log.debug("joinTableStatement: {}", statement.toString());
            addJoinTableStatementToStack(stack, statement);
        }
    }

    /**
     * Add the field to statement with as value the Id of the related entity,
     * taking into account the JPA name associated to the field.
     *
     * @param statement injected statement to modify
     * @param entity    entity to be parsed
     * @param field     relational field to add to statement
     */
    protected void addRelationalFiled(Statement statement, Object entity, Field field) {
        String fieldName = ReflectionUtils.getJoinColumnName(field);
        Object fieldValue = ReflectionUtils.getJoinColumnValue(entity, fieldName, field);
        log.debug("{} will be {} = {}", field.getName(), fieldName, fieldValue);
        statement.addField(fieldName, fieldValue);
    }

    /**
     * Add the field to statement taking into account the JPA name associated to the field.
     *
     * @param statement injected statement to modify
     * @param entity    entity to be parsed
     * @param field     field to add to statement
     */
    protected void addField(Statement statement, Object entity, Field field) {
        String fieldName = ReflectionUtils.getJPAColumnName(field);
        Object fieldValue = ReflectionUtils.getFieldValue(entity, field);
        log.debug("{} will be {} = {}", field.getName(), fieldName, fieldValue);
        statement.addField(fieldName, fieldValue);
    }

    /*---------------------------------------------------------------------------------*/
    /*----------------------------- BUILD FROM QUERY ----------------------------------*/
    /*---------------------------------------------------------------------------------*/

    /**
     * Main abstract algorithm that build statements from queries. Follows template pattern.
     * Abstract methods are implemented in sub classes, is possible to modify the standard behavior
     * of the algorithm overriding the hook methods (the protected ones).
     *
     * @param query       a {@link javax.persistence.Query} instance
     * @param queryString the JPQl string representation of the query
     *
     * @return a {@link java.util.Deque} used as stack containing the statements build from the given query
     */
    public Deque<Statement> build(Query query, String queryString) {
        Deque<Statement> stack = new ArrayDeque<>();
        log.info(queryString);
        List<Token> tokens = Lexer.lex(queryString);
        Statement statement = handleQuery(query, tokens);
        if (statement != null) {
            stack.addLast(statement);
        }
        return stack;
    }

    /**
     * Translate query tokens into a specific statement.
     * <p/>
     * Algorithm does not handle join tables since update or delete by query is not possible through JPA.
     * <p/>
     * Algorithm does not handle cascade type for now since currently Kundera does not support
     * dot notations in queries so is not possible to update through a query another entity
     * beside the one explicitly stated in the query.
     *
     * @param query  a {@link javax.persistence.Query} instance
     * @param tokens tokens obtained from lexer
     *
     * @return the generated {@link it.polimi.modaclouds.cpimlibrary.entitymng.statements.Statement}
     */
    protected abstract Statement handleQuery(Query query, List<Token> tokens);

    /**
     * Move the token iterator to find table name from where the iterator was left.
     * Take care of finding the correct JPA table name and modify the injected statement accordingly.
     *
     * @param tokenIterator injected token iterator
     * @param statement     injected statement to modify
     */
    protected void setTableName(Iterator<Token> tokenIterator, Statement statement) {
        String tableName = nextTokenOfType(TokenType.STRING, tokenIterator);
        log.debug("specified table name is {}", tableName);
        Class<?> clazz = getAssociatedClass(tableName);
        tableName = ReflectionUtils.getJPATableName(clazz);
        log.debug("JPA table name is {}", tableName);
        statement.setTable(tableName);
    }

    /**
     * Finds out the class that maps to the given tableName.
     *
     * @param tableName tableName
     *
     * @return the class instance
     */
    protected Class getAssociatedClass(String tableName) {
        String fullClassName = PersistenceMetadata.getInstance().getMappedClass(tableName);
        if (fullClassName == null) {
            throw new PersistenceMetadataException(tableName + " is unknown");
        }
        return ReflectionUtils.getClassInstance(fullClassName);
    }

    /**
     * Finds out the JPA name associated to the column name.
     *
     * @param column      a column token {@link it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.TokenType#COLUMN}
     * @param objectParam the JPQL object param to be removed from the column name
     * @param tableName   the table involved in the query
     *
     * @return the JPA name associated to the column name
     */
    protected String getJPAColumnName(Token column, String objectParam, String tableName) {
        String name = column.getData().replaceAll(objectParam + ".", "");
        Class<?> clazz = getAssociatedClass(tableName);
        Field field = ReflectionUtils.getFieldByName(clazz, name);
        return ReflectionUtils.getJPAColumnName(field);
    }

    /**
     * Move the iterator to fund out the first param from where
     * the iterator was left and search the param mapping in the given query.
     *
     * @param tokenIterator injected iterator
     * @param query         a {@link javax.persistence.Query} instance containing param mappings
     *
     * @return the value associated to the parameter
     */
    protected Object getNextParameterValue(Iterator<Token> tokenIterator, Query query) {
        String param = nextTokenOfType(TokenType.PARAM, tokenIterator).replaceAll(":|,", "");
        return query.getParameterValue(query.getParameter(param));
    }

    /**
     * Move the iterator until a token matching the requested type is found.
     *
     * @param type token type to look for {@link it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.TokenType}
     * @param itr  injected iterator
     *
     * @return the first matching token
     */
    protected String nextTokenOfType(TokenType type, Iterator<Token> itr) {
        Token current = itr.next();
        if (current.getType().equals(type)) {
            return current.getData();
        }
        return nextTokenOfType(type, itr);
    }
}
