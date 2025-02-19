package com.redis.om.spring.repository.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.repository.query.KeyValuePartTreeQuery;
import org.springframework.data.keyvalue.repository.query.SpelQueryCreator;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactory;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.PersistentEntityInformation;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.redis.om.spring.ops.RedisModulesOperations;
import com.redis.om.spring.repository.query.RediSearchQuery;

public class RedisDocumentRepositoryFactory extends RepositoryFactorySupport {

  private static final Class<SpelQueryCreator> DEFAULT_QUERY_CREATOR = SpelQueryCreator.class;

  private final KeyValueOperations keyValueOperations;
  private final RedisModulesOperations<?, ?> rmo;
  private final MappingContext<?, ?> context;
  private final Class<? extends AbstractQueryCreator<?, ?>> queryCreator;
  private final Class<? extends RepositoryQuery> repositoryQueryType;

  /**
   * Creates a new {@link KeyValueRepositoryFactory} for the given
   * {@link KeyValueOperations}.
   *
   * @param keyValueOperations must not be {@literal null}.
   */
  public RedisDocumentRepositoryFactory(KeyValueOperations keyValueOperations, RedisModulesOperations<?, ?> rmo) {
    this(keyValueOperations, rmo, DEFAULT_QUERY_CREATOR);
  }

  /**
   * Creates a new {@link KeyValueRepositoryFactory} for the given
   * {@link KeyValueOperations} and {@link AbstractQueryCreator}-type.
   *
   * @param keyValueOperations must not be {@literal null}.
   * @param queryCreator       must not be {@literal null}.
   */
  public RedisDocumentRepositoryFactory(KeyValueOperations keyValueOperations, RedisModulesOperations<?, ?> rmo,
      Class<? extends AbstractQueryCreator<?, ?>> queryCreator) {

    this(keyValueOperations, rmo, queryCreator, RediSearchQuery.class);
  }

  /**
   * Creates a new {@link KeyValueRepositoryFactory} for the given
   * {@link KeyValueOperations} and {@link AbstractQueryCreator}-type.
   *
   * @param keyValueOperations  must not be {@literal null}.
   * @param queryCreator        must not be {@literal null}.
   * @param repositoryQueryType must not be {@literal null}.
   */
  public RedisDocumentRepositoryFactory(KeyValueOperations keyValueOperations, RedisModulesOperations<?, ?> rmo,
      Class<? extends AbstractQueryCreator<?, ?>> queryCreator, Class<? extends RepositoryQuery> repositoryQueryType) {

    Assert.notNull(keyValueOperations, "KeyValueOperations must not be null!");
    Assert.notNull(queryCreator, "Query creator type must not be null!");
    Assert.notNull(repositoryQueryType, "RepositoryQueryType type must not be null!");

    this.keyValueOperations = keyValueOperations;
    this.rmo = rmo;
    this.context = keyValueOperations.getMappingContext();
    this.queryCreator = queryCreator;
    this.repositoryQueryType = repositoryQueryType;
  }

  /* (non-Javadoc)
   * 
   * @see
   * org.springframework.data.repository.core.support.RepositoryFactorySupport#
   * getEntityInformation(java.lang.Class) */
  @Override
  @SuppressWarnings("unchecked")
  public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {

    PersistentEntity<T, ?> entity = (PersistentEntity<T, ?>) context.getRequiredPersistentEntity(domainClass);

    return new PersistentEntityInformation<>(entity);
  }

  /* (non-Javadoc)
   * 
   * @see
   * org.springframework.data.repository.core.support.RepositoryFactorySupport#
   * getTargetRepository(org.springframework.data.repository.core.
   * RepositoryMetadata) */
  @Override
  protected Object getTargetRepository(RepositoryInformation repositoryInformation) {

    EntityInformation<?, ?> entityInformation = getEntityInformation(repositoryInformation.getDomainType());
    return super.getTargetRepositoryViaReflection(repositoryInformation, entityInformation, keyValueOperations, rmo);
  }

  /* (non-Javadoc)
   * 
   * @see
   * org.springframework.data.repository.core.support.RepositoryFactorySupport#
   * getRepositoryBaseClass(org.springframework.data.repository.core.
   * RepositoryMetadata) */
  @Override
  protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
    return SimpleRedisDocumentRepository.class;
  }

  /* (non-Javadoc)
   * 
   * @see
   * org.springframework.data.repository.core.support.RepositoryFactorySupport#
   * getQueryLookupStrategy(org.springframework.data.repository.query.
   * QueryLookupStrategy.Key,
   * org.springframework.data.repository.query.EvaluationContextProvider) */
  @Override
  protected Optional<QueryLookupStrategy> getQueryLookupStrategy(@Nullable Key key,
      QueryMethodEvaluationContextProvider evaluationContextProvider) {
    return Optional.of(new RediSearchQueryLookupStrategy(key, evaluationContextProvider, this.keyValueOperations,
        this.rmo, this.queryCreator, this.repositoryQueryType));
  }

  private static class RediSearchQueryLookupStrategy implements QueryLookupStrategy {

    private QueryMethodEvaluationContextProvider evaluationContextProvider;
    private KeyValueOperations keyValueOperations;
    private RedisModulesOperations<?, ?> rmo;

    private Class<? extends AbstractQueryCreator<?, ?>> queryCreator;
    private Class<? extends RepositoryQuery> repositoryQueryType;

    /**
     * @param key
     * @param evaluationContextProvider
     * @param keyValueOperations
     * @param queryCreator
     */
    public RediSearchQueryLookupStrategy(@Nullable Key key,
        QueryMethodEvaluationContextProvider evaluationContextProvider, KeyValueOperations keyValueOperations,
        RedisModulesOperations<?, ?> rmo, Class<? extends AbstractQueryCreator<?, ?>> queryCreator,
        Class<? extends RepositoryQuery> repositoryQueryType) {

      Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null!");
      Assert.notNull(keyValueOperations, "KeyValueOperations must not be null!");
      Assert.notNull(rmo, "RedisModulesOperations must not be null!");
      Assert.notNull(queryCreator, "Query creator type must not be null!");
      Assert.notNull(repositoryQueryType, "RepositoryQueryType type must not be null!");

      this.evaluationContextProvider = evaluationContextProvider;
      this.keyValueOperations = keyValueOperations;
      this.rmo = rmo;
      this.queryCreator = queryCreator;
      this.repositoryQueryType = repositoryQueryType;
    }

    /* (non-Javadoc)
     * 
     * @see
     * org.springframework.data.repository.query.QueryLookupStrategy#resolveQuery(
     * java.lang.reflect.Method,
     * org.springframework.data.repository.core.RepositoryMetadata,
     * org.springframework.data.projection.ProjectionFactory,
     * org.springframework.data.repository.core.NamedQueries) */
    @Override
    @SuppressWarnings("unchecked")
    public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
        NamedQueries namedQueries) {

      QueryMethod queryMethod = new QueryMethod(method, metadata, factory);

      Constructor<? extends KeyValuePartTreeQuery> constructor = (Constructor<? extends KeyValuePartTreeQuery>) ClassUtils
          .getConstructorIfAvailable(this.repositoryQueryType, QueryMethod.class, RepositoryMetadata.class,
              QueryMethodEvaluationContextProvider.class, KeyValueOperations.class, RedisModulesOperations.class,
              Class.class);

      Assert.state(constructor != null, String.format(
          "Constructor %s(QueryMethod, EvaluationContextProvider, KeyValueOperations, RedisModulesOperations, Class) not available!",
          ClassUtils.getShortName(this.repositoryQueryType)));

      return BeanUtils.instantiateClass(constructor, queryMethod, metadata, evaluationContextProvider,
          this.keyValueOperations, this.rmo, this.queryCreator);
    }
  }
}
