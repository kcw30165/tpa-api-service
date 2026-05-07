package com.bct.ngtpa.apiservice.config.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecution {

    String value() default "";

    boolean logArgs() default false;

    boolean logResult() default false;
}