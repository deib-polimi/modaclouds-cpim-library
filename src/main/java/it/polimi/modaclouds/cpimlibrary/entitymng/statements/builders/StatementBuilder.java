package it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders;

import it.polimi.modaclouds.cpimlibrary.entitymng.ReflectionUtils;
import it.polimi.modaclouds.cpimlibrary.entitymng.migration.MigrationManager;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.Statement;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.Lexer;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.Token;
import it.polimi.modaclouds.cpimlibrary.entitymng.statements.builders.lexer.TokenType;
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

    private final boolean followCascades;
    private final List<CascadeType> relevantCascadeTypes;
    private Deque<Statement> stack = new ArrayDeque<>();

    /**
     * Instantiate a builder that follow cascade types.
     *
     * @param relevantCascadeTypes a list of cascade types that need to be handled
     *
     * @see javax.persistence.CascadeType
     */
    public StatementBuilder(List<CascadeType> relevantCascadeTypes) {
        this.followCascades = true;
        this.relevantCascadeTypes = relevantCascadeTypes;
    }

    /**
     * Instantiate a builder that does not follow cascade types.
     *
     * @see javax.persistence.CascadeType
     */
    public StatementBuilder() {
        this.followCascades = false;
        this.relevantCascadeTypes = null;
    }

    protected void addToStack(Statement statement) {
        stack.addFirst(statement);
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
        Statement statement = initStatement();
        setTableName(statement, entity);

        Field[] fields = getFields(entity);
        for (Field field : fields) {
            if (ReflectionUtils.isRelational(field)) {
                log.debug("{} is a relational field", field.getName());
                if (ReflectionUtils.ownRelation(field)) {
                    log.debug("{} is the owning side of the relation", field.getName());
                    if (followCascades) {
                        handleCascade(entity, field);
                    } else {
                        log.info("Ignore cascade on field {}", field.getName());
                    }
                    if (ReflectionUtils.isFieldAnnotatedWith(field, ManyToMany.class)) {
                        log.debug("{} holds a ManyToMany relationship, handle JoinTable", field.getName());
                        handleJoinTable(entity, field);
                    } else {
                        onRelationalField(statement, entity, field);
                    }
                } else {
                    log.debug("{} is the non-owning side of the relation, ignore it", field.getName());
                    if (ReflectionUtils.isFieldAnnotatedWith(field, ManyToMany.class)) {
                        log.debug("{} holds a inverse ManyToMany relationship, handle JoinTable", field.getName());
                        handleInverseJoinTable(entity, field);
                    }
                }
            } else if (ReflectionUtils.isId(field)) {
                onIdField(statement, entity, field);
            } else {
                onFiled(statement, entity, field);
            }
        }

        addToStack(statement);
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
    protected void handleCascade(Object entity, Field field) {
        CascadeType[] declaredCascadeTypes = ReflectionUtils.getCascadeTypes(field);
        for (CascadeType cascadeType : declaredCascadeTypes) {
            if (this.relevantCascadeTypes.contains(cascadeType)) {
                Object cascadeEntity = ReflectionUtils.getFieldValue(entity, field);
                if (cascadeEntity instanceof Collection) {
                    for (Object cascade : (Collection) cascadeEntity) {
                        log.warn("Cascade operation on collection field {} with value {}", field.getName(), cascade);
                        build(cascade);
                    }
                } else {
                    log.warn("Cascade operation on field {} with value {}", field.getName(), cascadeEntity);
                    build(cascadeEntity);
                }
            }
        }
    }

    /**
     * From the owning side of the relationship, iterate through collection of related entities
     * and for each one calls {@link #generateJoinTableStatement(Object, Object, javax.persistence.JoinTable)}.
     *
     * @param entity entity to be parsed
     * @param field  the field owning the {@link javax.persistence.ManyToMany} relationship
     */
    protected void handleJoinTable(Object entity, Field field) {
        JoinTable joinTable = ReflectionUtils.getAnnotation(field, JoinTable.class);

        Collection collection = (Collection) ReflectionUtils.getFieldValue(entity, field);
        for (Object element : collection) {
            Statement statement = generateJoinTableStatement(entity, element, joinTable);
            if (statement != null) {
                log.info("joinTableStatement: {}", statement.toString());
                stack.addLast(statement);
            }
        }
    }

    /**
     * From the non-owning side of the relationship, prepare data to call
     * {@link #generateInverseJoinTableStatement(Object, javax.persistence.JoinTable)}.
     *
     * @param entity entity to be parsed
     * @param field  the field owning the inverse side of the {@link javax.persistence.ManyToMany} relationship
     */
    protected void handleInverseJoinTable(Object entity, Field field) {
        ManyToMany mtm = ReflectionUtils.getAnnotation(field, ManyToMany.class);
        String mappedBy = mtm.mappedBy();
        ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
        Class<?> ownerClass = (Class<?>) collectionType.getActualTypeArguments()[0];
        log.debug("{} is mapped by {} in class {}", field.getName(), mappedBy, ownerClass.getCanonicalName());

        Field ownerField = ReflectionUtils.getFieldByName(ownerClass, mappedBy);
        JoinTable joinTable = ReflectionUtils.getAnnotation(ownerField, JoinTable.class);
        Statement statement = generateInverseJoinTableStatement(entity, joinTable);
        if (statement != null) {
            log.info("joinTableStatement: {}", statement.toString());
            stack.addLast(statement);
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
        log.info(queryString);
        ArrayList<Token> tokens = Lexer.lex(queryString);
        Statement statement = handleQuery(query, tokens);
        if (statement != null) {
            addToStack(statement);
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
    protected abstract Statement handleQuery(Query query, ArrayList<Token> tokens);

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
    protected Class<?> getAssociatedClass(String tableName) {
        String fullClassName = MigrationManager.getInstance().getMappedClass(tableName);
        if (fullClassName == null) {
            throw new RuntimeException(tableName + " is unknown");
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
        String name = column.data.replaceAll(objectParam + ".", "");
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
        if (current.type.equals(type)) {
            return current.data;
        }
        return nextTokenOfType(type, itr);
    }
}