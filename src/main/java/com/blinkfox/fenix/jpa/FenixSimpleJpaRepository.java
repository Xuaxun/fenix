package com.blinkfox.fenix.jpa;

import com.blinkfox.fenix.helper.StringHelper;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 继承了 {@link SimpleJpaRepository} 类，实现了 {@link FenixJpaRepository} 接口的 Fenix JPA Repository 基础实现类.
 *
 * @author blinkfox on 2020-12-04.
 * @since v2.4.0
 */
public class FenixSimpleJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> implements FenixJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;

    private final EntityManager em;

    /**
     * 构造方法.
     *
     * @param entityInformation JPA 实体信息类，不能为 {@literal null}.
     * @param entityManager 实体管理器类，不能为 {@literal null}.
     */
    public FenixSimpleJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.em = entityManager;
    }

    /**
     * 构造方法.
     *
     * @param domainClass JPA 实体类的 class，不能为 {@literal null}.
     * @param em 实体管理器类，不能为 {@literal null}.
     */
    public FenixSimpleJpaRepository(Class<T> domainClass, EntityManager em) {
        this(JpaEntityInformationSupport.getEntityInformation(domainClass, em), em);
    }

    /**
     * 保存或更新实体类中非 null 属性的字段值.
     *
     * <ul>
     *     <li>如果实体的主键 ID 为空，说明是新增的情况，就插入一条新的数据；</li>
     *     <li>如果实体的主键 ID 不为空，会先判断是否存在该 ID 的数据，如果不存在也会新增插入一条数据；
     *     否则说明是更新的情况，会仅更新实体类属性中不为 null 值的属性字段到数据库中；</li>
     * </ul>
     *
     * @param entity 实体类
     * @return 原实体类，注意：如果是更新的情况，返回的值不一定有数据库中之前的值
     */
    @Transactional
    @Override
    public <S extends T> S saveOrUpdateNotNullProperties(S entity) {
        // 获取对象实体 ID，如果为空，就直接新增即可.
        ID id = (ID) this.entityInformation.getId(entity);
        if (StringHelper.isEmptyObject(id)) {
            this.em.persist(entity);
            return entity;
        }

        // 如果根据 ID 查询的实体不存在，也要新增插入一条新的记录.
        Optional<T> entityOptional = super.findById(id);
        if (!entityOptional.isPresent()) {
            this.em.persist(entity);
            return entity;
        }

        // 此时说明，该实体在数据库中已经存在，就将当前所有值非 null 的属性复制到原来的数据库实体对象中进行保存.
        T oldEntity = entityOptional.get();
        BeanUtils.copyProperties(entity, oldEntity, this.getNullProperties(entity));
        this.em.merge(oldEntity);
        return entity;
    }

    /**
     * 通过反射获取对象实体中所有值为 {@code null} 的属性名称的数组.
     *
     * @param entity 实体对象
     * @return 数组
     */
    private String[] getNullProperties(Object entity) {
        BeanWrapper beanWrapper = new BeanWrapperImpl(entity);
        PropertyDescriptor[] propertyDescriptors = beanWrapper.getPropertyDescriptors();
        List<String> nullProperties = new ArrayList<>();
        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            String propertyName = propertyDescriptor.getName();
            Object propertyValue = beanWrapper.getPropertyValue(propertyName);
            if (propertyValue == null) {
                nullProperties.add(propertyName);
            }
        }
        return nullProperties.toArray(new String[0]);
    }

}