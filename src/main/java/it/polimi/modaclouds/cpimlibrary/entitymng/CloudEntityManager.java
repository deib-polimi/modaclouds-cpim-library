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
package it.polimi.modaclouds.cpimlibrary.entitymng;

import it.polimi.modaclouds.cpimlibrary.entitymng.migration.MigrationManager;
import it.polimi.modaclouds.cpimlibrary.entitymng.migration.Statement;
import it.polimi.modaclouds.cpimlibrary.entitymng.migration.StatementBuilder;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;
import java.util.List;
import java.util.Map;

/**
 * Delegate every operation to the {@link javax.persistence.EntityManager} implementation
 * of the runtime provider except for persist method and createQuery methods.
 *
 * @author Fabio Arcidiacono.
 */
public class CloudEntityManager implements EntityManager {

    private MigrationManager migrator;
    private EntityManager delegate;

    public CloudEntityManager(EntityManager entityManager) {
        this.migrator = MigrationManager.getInstance();
        this.delegate = entityManager;
    }

    /**
     * In case of migration generates a SELECT statements
     * then sends it to the migration system.
     * Otherwise delegates to the persistence provider implementation.
     *
     * @see javax.persistence.EntityManager#persist(Object)
     */
    @Override
    public void persist(Object entity) {
        if (migrator.isMigrating()) {
            System.out.println("persist() MIGRATION");
            Statement statement = StatementBuilder.generateInsertStatement(entity);
            migrator.propagate(statement);
        } else {
            System.out.println("persist() DEFAULT implementation");
            delegate.persist(entity);
        }
    }

    @Override
    public <T> T merge(T entity) {
        return delegate.merge(entity);
    }

    @Override
    public void remove(Object entity) {
        delegate.remove(entity);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey) {
        return delegate.find(entityClass, primaryKey);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
        return delegate.find(entityClass, primaryKey, properties);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
        return delegate.find(entityClass, primaryKey, lockMode);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
        return delegate.find(entityClass, primaryKey, lockMode, properties);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        return delegate.getReference(entityClass, primaryKey);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
        delegate.setFlushMode(flushMode);
    }

    @Override
    public FlushModeType getFlushMode() {
        return delegate.getFlushMode();
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public void lock(Object entity, LockModeType lockMode) {
        delegate.lock(entity, lockMode);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        delegate.lock(entity, lockMode, properties);
    }

    @Override
    public void refresh(Object entity) {
        delegate.refresh(entity);
    }

    @Override
    public void refresh(Object entity, Map<String, Object> properties) {
        delegate.refresh(entity, properties);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public void refresh(Object entity, LockModeType lockMode) {
        delegate.refresh(entity, lockMode);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        delegate.refresh(entity, lockMode, properties);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public void detach(Object entity) {
        delegate.detach(entity);
    }

    @Override
    public boolean contains(Object entity) {
        return delegate.contains(entity);
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public LockModeType getLockMode(Object entity) {
        return delegate.getLockMode(entity);
    }

    @Override
    public void setProperty(String propertyName, Object value) {
        delegate.setProperty(propertyName, value);
    }

    @Override
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createQuery(String)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.CloudQuery
     */
    @Override
    public Query createQuery(String qlString) {
        return new CloudQuery(delegate.createQuery(qlString));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createQuery(javax.persistence.criteria.CriteriaQuery)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.TypedCloudQuery
     */
    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        return new TypedCloudQuery<>(delegate.createQuery(criteriaQuery));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createQuery(javax.persistence.criteria.CriteriaUpdate)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.CloudQuery
     */
    @Override
    public Query createQuery(CriteriaUpdate updateQuery) {
        return new CloudQuery(delegate.createQuery(updateQuery));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createQuery(javax.persistence.criteria.CriteriaDelete)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.CloudQuery
     */
    @Override
    public Query createQuery(CriteriaDelete deleteQuery) {
        return new CloudQuery(delegate.createQuery(deleteQuery));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createQuery(String, Class)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.TypedCloudQuery
     */
    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        return new TypedCloudQuery<>(delegate.createQuery(qlString, resultClass));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createNamedQuery(String)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.CloudQuery
     */
    @Override
    public Query createNamedQuery(String name) {
        return new CloudQuery(delegate.createNamedQuery(name));
    }

    /**
     * Delegates query generation to the persistence provider
     * then returns a wrapped query type.
     *
     * @see javax.persistence.EntityManager#createNamedQuery(String, Class)
     * @see it.polimi.modaclouds.cpimlibrary.entitymng.TypedCloudQuery
     */
    @Override
    public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
        return new TypedCloudQuery<>(delegate.createNamedQuery(name, resultClass));
    }

    /*
     * TODO decide
     * native query are expressed in underlying db native language
     * throw UnsupportedOperationException? or something else?
     */
    @Override
    public Query createNativeQuery(String sqlString) {
        //return delegate.createNativeQuery(sqlString);
        throw new UnsupportedOperationException("Native queries are currently not suppored");
    }

    @Override
    public Query createNativeQuery(String sqlString, Class resultClass) {
        //return delegate.createNativeQuery(sqlString, resultClass);
        throw new UnsupportedOperationException("Native queries are currently not suppored");
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        //return delegate.createNativeQuery(sqlString, resultSetMapping);
        throw new UnsupportedOperationException("Native queries are currently not suppored");
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
        return delegate.createStoredProcedureQuery(name);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
        return delegate.createStoredProcedureQuery(procedureName);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
        return delegate.createStoredProcedureQuery(procedureName, resultClasses);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
        return delegate.createStoredProcedureQuery(procedureName, resultSetMappings);
    }

    @Override
    public void joinTransaction() {
        delegate.joinTransaction();
    }

    /*
     * Note: Kundera[2.14] just return false
     */
    @Override
    public boolean isJoinedToTransaction() {
        return delegate.isJoinedToTransaction();
    }

    /*
     * Note: Kundera[2.14] will throw NotImplementedException()
     */
    @Override
    public <T> T unwrap(Class<T> cls) {
        return delegate.unwrap(cls);
    }

    @Override
    public Object getDelegate() {
        return delegate.getDelegate();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public EntityTransaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        // TODO return delegate.getEntityManagerFactory() or CloudEntityManagerFactory ?
        return null;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return delegate.getCriteriaBuilder();
    }

    @Override
    public Metamodel getMetamodel() {
        return delegate.getMetamodel();
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
        return delegate.createEntityGraph(rootType);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public EntityGraph<?> createEntityGraph(String graphName) {
        return delegate.createEntityGraph(graphName);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public EntityGraph<?> getEntityGraph(String graphName) {
        return delegate.getEntityGraph(graphName);
    }

    /*
     * Note: Kundera[2.14] just return null
     */
    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
        return delegate.getEntityGraphs(entityClass);
    }
}
