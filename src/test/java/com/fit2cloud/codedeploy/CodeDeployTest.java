package com.fit2cloud.codedeploy;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

public class CodeDeployTest extends HudsonTestCase {

    public void testConfig() throws Exception {
        HtmlPage page = new WebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "FIT2CLOUD CodeDeploy");
    }
}