package com.leydata.backend.template.infrastructure.persistence;

import java.time.OffsetDateTime;
<<<<<<< HEAD
=======
import java.util.UUID;
>>>>>>> origin/feature/templates-consentimiento

import org.springframework.data.jpa.domain.Specification;

import com.leydata.backend.entity.Templates;

public class TemplateSpecifications {

<<<<<<< HEAD
    public static Specification<Templates> hasTemplateKey(String templateKey) {
        return (root, query, cb) -> templateKey == null ? null :
            cb.equal(root.get("templateKey"), templateKey);
    }

    public static Specification<Templates> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null :
            cb.equal(root.get("isActive"), isActive);
    }

    // createdBy y approvedBy son String (Keycloak ID) en nuestra arquitectura
    public static Specification<Templates> createdBy(String createdBy) {
=======
    public static Specification<Templates> hasTemplateKey(String templateKey){
        return (root, query, cb) -> templateKey == null ? null : 
            cb.equal(root.get("templateKey"), templateKey);
    }

    public static Specification<Templates> isActive(Boolean isActive){
        return (root, query, cb) -> isActive == null ? null : 
            cb.equal(root.get("isActive"), isActive);
    }

    public static Specification<Templates> createdBy(UUID createdBy){
>>>>>>> origin/feature/templates-consentimiento
        return (root, query, cb) -> createdBy == null ? null :
            cb.equal(root.get("createdBy"), createdBy);
    }

<<<<<<< HEAD
    public static Specification<Templates> approvedBy(String approvedBy) {
=======
    public static Specification<Templates> approvedBy(UUID approvedBy){
>>>>>>> origin/feature/templates-consentimiento
        return (root, query, cb) -> approvedBy == null ? null :
            cb.equal(root.get("approvedBy"), approvedBy);
    }

<<<<<<< HEAD
    public static Specification<Templates> createdAfter(OffsetDateTime createdAfter) {
=======
    public static Specification<Templates> createdAfter(OffsetDateTime createdAfter){
>>>>>>> origin/feature/templates-consentimiento
        return (root, query, cb) -> createdAfter == null ? null :
            cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
    }

<<<<<<< HEAD
    public static Specification<Templates> createdBefore(OffsetDateTime createdBefore) {
        return (root, query, cb) -> createdBefore == null ? null :
            cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
    }
=======
    public static Specification<Templates> createdBefore(OffsetDateTime createdBefore){
        return (root, query, cb) -> createdBefore == null ? null :
            cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
    }
    
>>>>>>> origin/feature/templates-consentimiento
}
