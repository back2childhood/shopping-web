package com.shopping.Common.exception;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus
public class ResourceNotFoundException extends RuntimeException {
    private String resourceName;
    private String fieldName;
    private String fieldValue;

    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(String.format("%s not found with %s : %s", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() {return resourceName;}
    public String getFieldName() {return fieldName;}
    public String getFieldValue() {return fieldValue;}
    public void setResourceName(String resourceName) {this.resourceName = resourceName;}
    public void setFieldName(String fieldName) {this.fieldName = fieldName;}
    public void setFieldValue(String fieldValue) {this.fieldValue = fieldValue;}
}
