package com.blinkfox.fenix.jpa;

import com.blinkfox.fenix.bean.SqlInfo;
import com.blinkfox.fenix.core.Fenix;
import com.blinkfox.fenix.helper.QueryHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Tuple;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.AbstractJpaQuery;
import org.springframework.data.jpa.repository.query.JpaParameters;
import org.springframework.data.jpa.repository.query.JpaQueryMethod;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ReturnedType;

/**
 * 继承了 {@link AbstractJpaQuery} 抽象类，
 * 并隐性实现了 {@link RepositoryQuery} 接口的 JPA 查询处理器类，
 * 该类主要用来处理标注了 {@link QueryFenix} 注解的 JPA 查询.
 *
 * @author blinkfox on 2019-08-04.
 */
@Slf4j
public class FenixJpaQuery extends AbstractJpaQuery {

    /**
     * JPA 参数对象.
     */
    private JpaParameters jpaParams;

    /**
     * 标注了 {@link QueryFenix} 注解的注解实例.
     */
    private QueryFenix queryFenix;

    /**
     * JPQL 或者 SQL 语句.
     */
    private String sql;

    /**
     * Creates a new {@link AbstractJpaQuery} from the given {@link JpaQueryMethod}.
     *
     * @param method JpaQueryMethod
     * @param em EntityManager
     */
    FenixJpaQuery(JpaQueryMethod method, EntityManager em, QueryFenix queryFenix) {
        super(method, em);
        this.queryFenix = queryFenix;
    }

    /**
     * Creates a {@link Query} instance for the given values.
     *
     * @param values must not be {@literal null}.
     * @return Query
     */
    @Override
    protected Query doCreateQuery(Object[] values) {
        this.jpaParams = getQueryMethod().getParameters();
        // 获取 QueryFenix 注解中的全 fenixId 和上下文参数，来从 XML 文件中动态构建出 SQL 信息.
        SqlInfo sqlInfo = Fenix.getSqlInfo(queryFenix.value(), this.buildContextParams(values));
        this.sql = sqlInfo.getSql();

        // 判断是否有分页参数.如果有的话，就设置分页参数.
        Pageable pageable = this.buildPagableAndSortSql(values);

        // 构建出 SQL 查询和相关的参数.
        Query query;
        EntityManager em = super.getEntityManager();
        if (queryFenix.nativeQuery()) {
            ParameterAccessor accessor = new ParametersParameterAccessor(getQueryMethod().getParameters(), values);
            Class<?> type = getTypeToQueryFor(getQueryMethod().getResultProcessor()
                    .withDynamicProjection(accessor).getReturnedType());
            if (type == null) {
                query = em.createNativeQuery(this.sql);
            } else {
                query = em.createNativeQuery(this.sql, type);
            }
        } else {
            query = em.createQuery(this.sql);
        }

        // 循环设置命名绑定参数，且如果分页对象不为空，就设置分页参数.
        sqlInfo.getParams().forEach(query::setParameter);
        if (pageable != null) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        return query;
    }

    /**
     * 根据返回类型获取对应的 class.
     *
     * @param returnedType ReturnedType 实例
     * @return class
     */
    private Class<?> getTypeToQueryFor(ReturnedType returnedType) {
        Class<?> result = getQueryMethod().isQueryForEntity() ? returnedType.getDomainType() : null;

        // 如果 sql 中有构造器表达式或者投影，就直接返回该结果.
        if (QueryUtils.hasConstructorExpression(this.sql)
                || QueryUtils.getProjection(this.sql).equalsIgnoreCase(QueryHelper.detectAlias(this.sql))) {
            return result;
        }

        return returnedType.isProjecting() && !getMetamodel().isJpaManaged(returnedType.getReturnedType())
                ? Tuple.class
                : result;
    }

    /**
     * 根据 JPA 参数和值的数组来分析构建出 JPQL 语句和参数的 Map 型上下文参数.
     *
     * @param values JPA 参数值
     * @return Map
     */
    private Map<String, Object> buildContextParams(Object[] values) {
        int len = jpaParams.getNumberOfParameters();
        Map<String, Object> context = new HashMap<>(len);
        for (int i = 0; i < len; i++) {
            Parameter parameter = jpaParams.getParameter(i);
            if (parameter.isSpecialParameter()) {
                continue;
            }

            // 获取参数
            Optional<String> nameOptional =  parameter.getName();
            if (nameOptional.isPresent()) {
                context.put(nameOptional.get(), values[i]);
            }
        }
        return context;
    }

    /**
     * 继续构建 Spring Data JPA 分页和排序参数的SQL.
     *
     * @param values 参数数组
     */
    private Pageable buildPagableAndSortSql(Object[] values) {
        Pageable pageable = null;
        if (jpaParams.hasPageableParameter()) {
            pageable = (Pageable) (values[jpaParams.getPageableIndex()]);
            if (pageable != null) {
                Sort sort = pageable.getSort();
                if (sort != null) {
                    this.sql = QueryUtils.applySorting(this.sql, sort, QueryHelper.detectAlias(this.sql));
                }
            }
        }

        // 判断是否有排序参数，如果有，就追加排序相关的参数.
        if (jpaParams.hasSortParameter()) {
            this.sql = QueryUtils.applySorting(this.sql, new ParametersParameterAccessor(jpaParams, values).getSort(),
                    QueryHelper.detectAlias(this.sql));
        }
        return pageable;
    }

    /**
     * Creates a TypedQuery for counting using the given values.
     *
     * @param values must not be {@literal null}.
     * @return Query
     */
    @Override
    protected Query doCreateCountQuery(Object[] values) {
        return null;
    }

}
