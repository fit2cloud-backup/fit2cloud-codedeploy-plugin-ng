package com.fit2cloud.codedeploy;

import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClient;
import org.jfrog.artifactory.client.model.File;

/**
 * Created by linjinbo on 2017/8/30.
 */
public class ArtifactoryUploader {
    public String uploadArtifactory(java.io.File file,String artifactoryUrl,String username,String password,String repoName){
        Artifactory artifactory = ArtifactoryClient.create(artifactoryUrl, username, password);
//        ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl().apiUrl("api/build")
//                .method(ArtifactoryRequest.Method.GET)
//                .requestType(ArtifactoryRequest.ContentType.JSON);
//        List<String> response = artifactory.restCall(repositoryRequest);
        //Users/linjinbo/Documents/settingl.xml
        //Users/linjinbo/FIT2CLOUD/fit2cloud-codedeploy-plugin-ng/target/codedeploy.hpi
        File result = artifactory.repository(repoName).upload("fit2cloud", file).doUpload();
        return result.getUri();
    }
}
