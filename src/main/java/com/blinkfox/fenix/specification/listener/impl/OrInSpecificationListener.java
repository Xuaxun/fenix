package com.blinkfox.fenix.specification.listener.impl;

import com.blinkfox.fenix.specification.annotation.OrIn;
import com.blinkfox.fenix.specification.listener.AbstractSpecificationListener;
import com.blinkfox.fenix.specification.predicate.FenixBooleanStaticPredicate;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Predicate.BooleanOperator;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.query.criteria.internal.CriteriaBuilderImpl;
import org.springframework.stereotype.Component;

/**
 * 构建“或者范围匹配条件”({@code field IN ('xxx', 'yyy')})场景的 Specification 监听器.
 *
 * @author YangWenpeng on 2019-12-17
 * @author blinkfox on 2020-01-14
 * @since v2.2.0
 */
@Slf4j
@Component
public class OrInSpecificationListener extends AbstractSpecificationListener {

    @Override
    protected <Z, X> Predicate buildPredicate(
            CriteriaBuilder criteriaBuilder, From<Z, X> from, String name, Object value, Object annotation) {
        Path<Object> path = from.get(name);
        CriteriaBuilder.In<Object> in = criteriaBuilder.in(path);
        // TODO 需要考虑数组的场景.
        if (value instanceof Collection) {
            Collection<?> list = (Collection<?>) value;
            if (list.isEmpty()) {
                return new FenixBooleanStaticPredicate(
                        (CriteriaBuilderImpl) criteriaBuilder, false, BooleanOperator.OR);
            } else {
                list.forEach(in::value);
            }
        } else {
            in.value(value);
        }
        return criteriaBuilder.or(
                this.getAllowNull(annotation) ? criteriaBuilder.or(in, criteriaBuilder.isNull(path)) : in);
    }

    private boolean getAllowNull(Object annotation) {
        try {
            return (boolean) getAnnotation().getMethod("allowNull").invoke(annotation);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            log.error("【Fenix 错误提示】获取【@In】注解中【allowNull】时失败，将默认该值为 false.", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<OrIn> getAnnotation() {
        return OrIn.class;
    }

}
