package com.firstgit.api.dto;

/**
 * Standardized response DTO for deployment operations.
 */
public class DeployResponse {

    private String status;
    private String repositoryUrl;

    public DeployResponse() {}

    public DeployResponse(String status, String repositoryUrl) {
        this.status = status;
        this.repositoryUrl = repositoryUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
