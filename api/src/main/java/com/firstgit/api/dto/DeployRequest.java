package com.firstgit.api.dto;

/**
 * Request DTO for deployment operations.
 */
public class DeployRequest {

    private String repoName;
    private boolean isPrivate;

    public DeployRequest() {}

    public DeployRequest(String repoName, boolean isPrivate) {
        this.repoName = repoName;
        this.isPrivate = isPrivate;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}
