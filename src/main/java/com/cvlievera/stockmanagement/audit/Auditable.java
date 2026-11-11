package com.cvlievera.stockmanagement.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    String action();

    String entityType();

    /**
     * Index of the method parameter that holds the entity ID.
     * Use -1 (default) to extract the ID from the return value's getId() method.
     */
    int entityIdParam() default -1;
}
