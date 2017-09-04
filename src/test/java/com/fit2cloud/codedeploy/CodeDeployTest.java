package com.fit2cloud.codedeploy;

import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.IOException;

public class CodeDeployTest extends HudsonTestCase {
    private ArtifactoryUploader artifactoryUploader;
//    public void testConfig() throws Exception {
//        HtmlPage page = new WebClient().goTo("configure");
//        WebAssert.assertTextPresent(page, "FIT2CLOUD CodeDeploy");
//    }
    @Test
    public void test() throws IOException {
        File file = new File("/Users/linjinbo/Downloads/download.zip");
        String server = "http://163.53.91.134:8081";
        String username = "admin";
        String password = "fit2cloud";
        String repo = "http://163.53.91.134:8081/artifactory/example-repo-local";
        String path = "com/fit2cloud/plugins";
        String url = artifactoryUploader.uploadArtifactory(file,server,username,password,repo,path);
        System.out.println("result:"+url);
    }

    public ArtifactoryUploader getArtifactoryUploader() {
        return artifactoryUploader;
    }

    public void setArtifactoryUploader(ArtifactoryUploader artifactoryUploader) {
        this.artifactoryUploader = artifactoryUploader;
    }
}
