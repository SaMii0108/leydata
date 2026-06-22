package com.leydata.backend.template.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.leydata.backend.entity.Templates;

public class TemplateSpecifications {

    public static Specification<Templates> hasTemplateKey(String templateKey){
        return (root, query, cb) -> templateKey == null ? null : 
            cb.equal(root.get("templateKey"), templateKey);
    }

    public static Specification<Templates> isActive(Boolean isActive){
        return (root, query, cb) -> isActive == null ? null : 
            cb.equal(root.get("isActive"), isActive);
    }

    public static Specification<Templates> createdBy(UUID createdBy){
        return (root, query, cb) -> createdBy == null ? null :
            cb.equal(root.get("createdBy"), createdBy);
    }

    public static Specification<Templates> approvedBy(UUID approvedBy){
        return (root, query, cb) -> approvedBy == null ? null :
            cb.equal(root.get("approvedBy"), approvedBy);
    }

    public static Specification<Templates> createdAfter(OffsetDateTime createdAfter){
        return (root, query, cb) -> createdAfter == null ? null :
            cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
    }

    public static Specification<Templates> createdBefore(OffsetDateTime createdBefore){
        return (root, query, cb) -> createdBefore == null ? null :
            cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
    }
    
}
