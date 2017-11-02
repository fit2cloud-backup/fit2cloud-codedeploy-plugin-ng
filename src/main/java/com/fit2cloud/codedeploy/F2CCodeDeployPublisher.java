package com.fit2cloud.codedeploy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fit2cloud.sdk.model.*;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.fit2cloud.sdk.Fit2CloudClient;
import com.fit2cloud.sdk.Fit2CloudException;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.DirScanner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

public class F2CCodeDeployPublisher extends Publisher {
    private static final String LOG_PREFIX = "[FIT2CLOUD 代码部署]";
	public static final long      DEFAULT_TIMEOUT_SECONDS           = 900;
    public static final long      DEFAULT_POLLING_FREQUENCY_SECONDS = 15;
    public static final String    ROLE_SESSION_NAME                 = "jenkins-codedeploy-plugin";
    private static final String SYSTEM_FILE_SEPARATOR = "/";
    private static final String SYSTEM_OS = System.getProperty("os.name").toLowerCase().startsWith("win") ? "windows" : "linux";

    private final String f2cEndpoint;
    private final String f2cAccessKey;
    private final String f2cSecretKey;

    private final Long applicationRepoId;
    private final Long applicationId;
    private final String applicationVersion;

    private final String includes;
    private final String excludes;
    private final String appspecFilePath;
    private final String description;

    private final String artifactType;
    private final String nexusGroupId;
    private final String nexusArtifactId;
    private final String nexusArtifactVersion;
    private final boolean autoDeploy;
    private final boolean waitForCompletion;
    private final Long pollingTimeoutSec;
    private final Long pollingFreqSec;

    private final boolean nexusChecked;
    private final boolean artifactoryChecked;
    private final boolean ossChecked;
    private final boolean s3Checked;

    private final Long clusterId;
    private final Long clusterRoleId;
    private final Long serverId;
    private final Long contactGroupId;
    private final String deployStrategy;
    private final String path;

    //上传到阿里云参数
	private final String objectPrefixAliyun;

	//上传到亚马逊参数
	private final String objectPrefixAWS;

    private PrintStream logger;

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public F2CCodeDeployPublisher(String f2cEndpoint, String f2cAccessKey, String f2cSecretKey, Long applicationRepoId,
								  Long applicationId, String applicationVersion, String includes, String excludes, Boolean autoDeploy,
								  String artifactType, String nexusGroupId, String nexusArtifactId, String nexusArtifactVersion, Boolean waitForCompletion, Long pollingTimeoutSec,
								  Long pollingFreqSec, boolean nexusChecked, boolean artifactoryChecked, boolean ossChecked, boolean s3Checked, Long clusterId, Long clusterRoleId, Long serverId, Long contactGroupId,
								  String deployStrategy, String path, String description, String appspecFilePath, String objectPrefixAliyun, String objectPrefixAWS) {
		this.f2cEndpoint = f2cEndpoint;
		this.f2cAccessKey = f2cAccessKey;
		this.f2cSecretKey = f2cSecretKey;
		this.applicationRepoId = applicationRepoId;
		this.applicationId = applicationId;
		this.applicationVersion = applicationVersion;
		this.includes = includes;
		this.excludes = excludes;
		this.nexusGroupId = nexusGroupId;
		this.nexusArtifactId = nexusArtifactId;
		this.nexusArtifactVersion = nexusArtifactVersion;
		this.nexusChecked = artifactType.equals("nexus")?true:false;
		this.artifactoryChecked = artifactType.equals("artifactory")?true:false;
		this.ossChecked = artifactType.equals("oss")?true:false;
		this.s3Checked = artifactType.equals("s3")?true:false;;
		this.clusterId = clusterId;
		this.clusterRoleId = clusterRoleId;
		this.serverId = serverId;
		this.contactGroupId = contactGroupId;
		this.deployStrategy = deployStrategy;
		this.path = path;
		this.artifactType = StringUtils.isBlank(artifactType) ? ArtifactType.NEXUS : artifactType;
		this.autoDeploy = autoDeploy == null ? false : autoDeploy;
		this.description = description;
		this.appspecFilePath = StringUtils.isBlank(appspecFilePath) ? "appspec.yml" : appspecFilePath;
		this.objectPrefixAliyun = objectPrefixAliyun;
		this.objectPrefixAWS = objectPrefixAWS;

		if (waitForCompletion != null && waitForCompletion) {
            this.waitForCompletion = true;
            if (pollingTimeoutSec == null || pollingTimeoutSec <= 0) {
                this.pollingTimeoutSec = DEFAULT_TIMEOUT_SECONDS;
            } else {
                this.pollingTimeoutSec = pollingTimeoutSec;
            }
            if (pollingFreqSec == null|| pollingFreqSec <= 0) {
                this.pollingFreqSec = DEFAULT_POLLING_FREQUENCY_SECONDS;
            } else {
                this.pollingFreqSec = pollingFreqSec;
            }
        } else {
            this.waitForCompletion = false;
            this.pollingTimeoutSec = null;
            this.pollingFreqSec = null;
        }
    }

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        this.logger = listener.getLogger();
        final boolean buildFailed = build.getResult() == Result.FAILURE;
        if (buildFailed) {
            log("Skipping CodeDeploy publisher as build failed");
            return true;
        }

        FilePath workspace = build.getWorkspace();
        // ---------------- 开始校验各项输入 ----------------
        String newAppVersion = Utils.replaceTokens(build, listener, this.applicationVersion);

		final Fit2CloudClient fit2CloudClient = new Fit2CloudClient(this.f2cAccessKey, this.f2cSecretKey, this.f2cEndpoint);

		ApplicationRepo repo = null;
		try {
			log("开始校验应用仓库 ...");
			repo = fit2CloudClient.getApplicationRepo(applicationRepoId);
		} catch (Fit2CloudException e) {
		} finally {
			if(repo == null) {
				log("仓库无效,无法注册到FIT2CLOUD.");
				return false;
			}
		}
		log("应用仓库 ... OK");

		Application app = null;
		try {
			log("开始校验应用 ...");
			app = fit2CloudClient.getApplication(applicationId);
		} catch (Fit2CloudException e) {
		} finally {
			if(app == null) {
				log("应用无效,无法注册到FIT2CLOUD.");
				return false;
			}
		}
		log("应用 ... OK");

		String repoType = repo.getType();
		if(!artifactType.equalsIgnoreCase(repoType)) {
			log("所选仓库与 \"Zip文件上传设置\"中的类型设置不匹配!");
			return false;
		}

		Cluster cluster = null;
		String clusterRoleName = null;
		if(autoDeploy) {
			try {
				log("开始校验集群 ...");
				cluster = fit2CloudClient.getCluster(clusterId);
			} catch (Fit2CloudException e) {
			} finally {
				if(cluster == null) {
					log("目标集群无效,无法注册到FIT2CLOUD.");
					return false;
				}
			}
			log("集群 ... OK");

			try {
				log("获取虚机组信息 ...");
				ClusterRole clusterRole = fit2CloudClient.getClusterRole(clusterRoleId);
				if(clusterRole != null) {
					clusterRoleName = clusterRole.getName();
				}
			} catch (Fit2CloudException e) {
			}
		}
		// ---------------- 结束校验各项输入 ----------------

		// ---------------- 开始生成zip包并上传 ----------------
		int buildNumber = build.getNumber();
		String prjName = build.getProject().getName();
		File zipFile = null;
		String newAddress = null;
        try {
        	String zipFileName = prjName+"-"+buildNumber+".zip";
        	zipFile = zipFile(zipFileName, workspace);

        	switch (artifactType) {
			case ArtifactType.NEXUS:
				if(StringUtils.isBlank(nexusArtifactId) || StringUtils.isBlank(nexusGroupId) || StringUtils.isBlank(nexusArtifactVersion)) {
					log("请输入上传至 Nexus 的 GroupId、 ArtifactId 和 NexusArtifactVersion");
					return false;
				}
				log("开始上传zip文件到nexus服务器");
				try {
					newAddress = NexusUploader.upload(zipFile, repo.getAccessId(), repo.getAccessPassword(), repo.getRepo(), nexusGroupId, nexusArtifactId, String.valueOf(buildNumber), "zip", nexusArtifactVersion);
				} catch (Exception e) {
					log("上传文件到 Nexus 服务器失败！错误消息如下:");
		            log(e.getMessage());
		            e.printStackTrace(this.logger);
		            return false;
				}
				log("上传zip文件到nexus服务器成功!");
				break;
			case ArtifactType.ARTIFACTORY:
				log("开始上传zip文件到Artifactory服务器");
				try {
					newAddress = ArtifactoryUploader.uploadArtifactory(zipFile,repo.getServer().trim(),repo.getAccessId(), repo.getAccessPassword(), repo.getRepo(),path);
				} catch (Exception e) {
					log("上传文件到 Artifactory 服务器失败！错误消息如下:");
					log(e.getMessage());
					e.printStackTrace(this.logger);
					return false;
				}
				log("上传zip文件到Artifactory服务器成功!");
				break;
			case ArtifactType.OSS:
				log("开始上传zip文件到OSS服务器");
				//getBucketLocation
				String expFP = Utils.replaceTokens(build, listener, zipFile.toString());

				if (expFP != null) {
					expFP = expFP.trim();
				}

				// Resolve virtual path
				String expVP = Utils.replaceTokens(build, listener, objectPrefixAliyun);
				if (Utils.isNullOrEmpty(expVP)) {
					expVP = null;
				}
				if (!Utils.isNullOrEmpty(expVP) && !expVP.endsWith(Utils.FWD_SLASH)) {
					expVP = expVP.trim() + Utils.FWD_SLASH;
				}
				try {
					int filesUploaded = AliyunOSSClient.upload(build, listener,
							repo.getAccessId(),
							repo.getAccessPassword(),
							".aliyuncs.com",
							repo.getRepo(), expFP, expVP);
					if (filesUploaded>0) {
						log("上传Artifacts到阿里云OSS成功!");
					}
				} catch (Exception e) {
					log("上传Artifact到阿里云OSS失败，错误消息如下:");
					log(e.getMessage());
					e.printStackTrace(this.logger);
					return false;
				}
				log("上传zip文件到oss服务器成功!");
				if (expVP ==null){
					newAddress = zipFile.getName();
				}else {
					newAddress = objectPrefixAliyun+"/"+zipFile.getName();
				}
				log("文件路径"+newAddress);
				break;
			case ArtifactType.S3:
				log("开始上传zip文件到AWS服务器");
				//getBucketLocation
				String expFPAws = Utils.replaceTokens(build, listener, zipFile.toString());

				if (expFPAws != null) {
					expFPAws = expFPAws.trim();
				}

				// Resolve virtual path
				String expVPAws = Utils.replaceTokens(build, listener, objectPrefixAWS);
				if (Utils.isNullOrEmpty(expVPAws)) {
					expVPAws = null;
				}
				if (!Utils.isNullOrEmpty(expVPAws) && !expVPAws.endsWith(Utils.FWD_SLASH)) {
					expVPAws = expVPAws.trim() + Utils.FWD_SLASH;
				}
				try {
					AWSS3Client.upload(build, listener,
							repo.getAccessId(),
							repo.getAccessPassword(),
							null,
							repo.getRepo(), expFPAws, expVPAws);
						log("上传Artifacts到亚马逊AWS成功!");
				} catch (Exception e) {
					log("上传Artifact到亚马逊AWS失败，错误消息如下:");
					log(e.getMessage());
					e.printStackTrace(this.logger);
					return false;
				}
				log("上传zip文件到亚马逊AWS服务器成功!");
				if (expVPAws ==null){
					newAddress = zipFile.getName();
				}else {
					newAddress = objectPrefixAWS+"/"+zipFile.getName();
				}
				log("文件路径:"+newAddress);
				break;
			default:
				log("暂时不支持 "+artifactType+" 类型制品库");
				return false;
			}
        }catch(Exception e) {
        	e.printStackTrace();
        }finally {
        	if(zipFile != null) {
        		final boolean deleted = zipFile.delete();
        		if (!deleted) {
        			log("删除zip文件失败 : " + zipFile.getPath());
        		}else {
        			log("成功删除zip文件 : " + zipFile.getPath());
        		}
        	}
        }
        // ---------------- 结束生成zip包并上传 ----------------

        // ---------------- 开始注册应用版本 ----------------
        ApplicationRevision applicationRevision = null;
		try {
			applicationRevision = fit2CloudClient.addApplicationRevision(newAppVersion,description,app.getName(),repo.getName(),newAddress,null);
			log("注册应用版本成功: 新版本Id是"+applicationRevision.getId());
		} catch (Fit2CloudException e) {
			log("注册FIT2CLOUD应用版本失败，错误消息如下:");
            log(e.getMessage());
            e.printStackTrace(this.logger);
            return false;
		}
		// ---------------- 结束注册应用版本 ----------------

		// ---------------- 开始自动部署应用 ----------------
		if(autoDeploy){
            try {
                log("开始自动部署新注册的应用版本...");
                ApplicationDeployment applicationDeployment = fit2CloudClient.addDeployment(applicationRevision.getApplicationName()
                        ,applicationRevision.getName()
                        ,cluster.getName()
                        ,clusterRoleName
                        ,serverId
                        ,deployStrategy
                        ,"Jenkins触发"
                        ,contactGroupId);

                log("触发FIT2CLOUD代码部署成功。");

                if(waitForCompletion) {

                	HashMap<String, String> deploymentStatusMap = new HashMap<String, String>();
                	deploymentStatusMap.put("pendding", "等待部署");
                	deploymentStatusMap.put("executing", "部署中");
                	deploymentStatusMap.put("successed", "部署成功");
                	deploymentStatusMap.put("failed", "部署失败");
                	deploymentStatusMap.put("canceled", "取消部署");
                	log(applicationRevision.getApplicationName()+"的部署状态:");
                	int i = 0;
                	boolean success = true;
                	while(true) {
                		try {
                			Thread.sleep(pollingFreqSec*1000);
                		} catch (InterruptedException e) {
                		}
                		boolean allFinished = true;
                		success = true;
                		List<ApplicationDeploymentLog> logs = fit2CloudClient.getDeploymentLogs(applicationDeployment.getId());
                		for(ApplicationDeploymentLog log : logs){
                			log("主机:"+log.getServerName()+ "->" +deploymentStatusMap.get(log.getStatus()));
                			if(log.getStatus().equals("failed")){
                				log("部署失败原因如下：");
								List<ApplicationDeploymentEventLog> eventLogs = fit2CloudClient.getDeploymentEventLogs(applicationDeployment.getId());
								if (eventLogs !=null){
									for (ApplicationDeploymentEventLog eventLog:eventLogs){
										if (eventLog.getStatus().equals("failed")){
											log("执行"+eventLog.getEventName()+".sh步骤出错"+":");
											log(eventLog.getMsg());
										}
									}
								}
                				success = false;
                			}
                			if(log.getStatus().equals("executing")||log.getStatus().equals("pendding")){
                				allFinished = false;
                			}
                		}
                		if(allFinished){
                			if(success){
                				log("部署成功！");
                				break;
                			}else{
								return false;
                			}
                		}
                		if(pollingFreqSec * ++i > pollingTimeoutSec){
                			this.log("部署超时,请查看FIT2CLOUD控制台！");
                			return false;
                		}
                	}
                }else {
                    log("具体部署结果请登录FIT2CLOUD控制台查看。");
                }
            }catch (Exception e){
                log("触发FIT2CLOUD代码部署失败，错误消息如下:");
                log(e.getMessage());
                e.printStackTrace(this.logger);
                return false;
            }
        }
		// ---------------- 结束自动部署应用 ----------------
        return true;
    }


    private File zipFile(String zipFileName, FilePath sourceDirectory) throws IOException, InterruptedException, IllegalArgumentException {
    	FilePath appspecFp = new FilePath(sourceDirectory, appspecFilePath);

    	log("指定 appspecPath ::::: "+appspecFp.toURI().getPath());
        if (appspecFp.exists()) {
    		if(!"appspec.yml".equals(appspecFilePath)) {
    			FilePath appspecDestFP = new FilePath(sourceDirectory, "appspec.yml");
    			log("目标 appspecPath  ::::: "+appspecDestFP.toURI().getPath());
    			appspecFp.copyTo(appspecDestFP);
    		}
            log("成功添加appspec文件");
        }else {
            throw new IllegalArgumentException("没有找到对应的appspec.yml文件！" );
        }
        File zipFile = new File("/tmp/"+ zipFileName);
		final boolean fileCreated = zipFile.createNewFile();
		if (!fileCreated) {
			log("Zip文件已存在，开始覆盖 : " + zipFile.getPath());
		}

        log("生成Zip文件 : " + zipFile.getAbsolutePath());

        FileOutputStream outputStream = new FileOutputStream(zipFile);
        try {
        	String allIncludes = includes + ",appspec.yml";
            sourceDirectory.zip(
                    outputStream,
                    new DirScanner.Glob(allIncludes, this.excludes)
            );
        } finally {
            outputStream.close();
        }
        return zipFile;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {

        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    	public FormValidation doCheckAccount(
                @QueryParameter String f2cAccessKey,
                @QueryParameter String f2cSecretKey,
                @QueryParameter String f2cEndpoint) {
            if (StringUtils.isEmpty(f2cAccessKey)) {
                return FormValidation.error("FIT2CLOUD ConsumerKey不能为空！");
            }
            if (StringUtils.isEmpty(f2cSecretKey)) {
                return FormValidation.error("FIT2CLOUD SecretKey不能为空！");
            }
            if (StringUtils.isEmpty(f2cEndpoint)) {
                return FormValidation.error("FIT2CLOUD EndPoint不能为空！");
            }
            try {
                Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
                fit2CloudClient.getClusters();
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok("验证FIT2CLOUD帐号成功！");
        }

		/**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
        	super(F2CCodeDeployPublisher.class);
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "FIT2CLOUD 代码部署";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindParameters(this);
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillApplicationRepoIdItems(@QueryParameter String f2cAccessKey,
                @QueryParameter String f2cSecretKey,
                @QueryParameter String f2cEndpoint) {
            ListBoxModel items = new ListBoxModel();
        	try {
        		Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
    			List<ApplicationRepo> list = fit2CloudClient.getApplicationRepoList(null, null);
    			if(list != null && list.size() > 0) {
    				for(ApplicationRepo c : list) {
    					items.add(c.getName()+"   ("+c.getType()+")", String.valueOf(c.getId()));
    				}
    			}
        	} catch (Exception e) {
//            		e.printStackTrace();
//                return FormValidation.error(e.getMessage());
        	}
            return items;
        }

        public ListBoxModel doFillApplicationIdItems(@QueryParameter String f2cAccessKey,
        		@QueryParameter String f2cSecretKey,
        		@QueryParameter String f2cEndpoint) {
        	ListBoxModel items = new ListBoxModel();
    		try {
    			Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
    			List<Application> list = fit2CloudClient.getApplicationList(null, null);
    			if(list != null && list.size() > 0) {
    				for(Application c : list) {
    					items.add(c.getName(), String.valueOf(c.getId()));
    				}
    			}
    		} catch (Exception e) {
//    			e.printStackTrace();
//                return FormValidation.error(e.getMessage());
    		}
        	return items;
        }

        public ListBoxModel doFillClusterIdItems(@QueryParameter String f2cAccessKey,
        		@QueryParameter String f2cSecretKey,
        		@QueryParameter String f2cEndpoint) {
        	ListBoxModel items = new ListBoxModel();
        	try {
        		Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
        		List<Cluster> list = fit2CloudClient.getClusters();
        		if(list != null && list.size() > 0) {
        			for(Cluster c : list) {
        				items.add(c.getName(), String.valueOf(c.getId()));
        			}
        		}
        	} catch (Exception e) {
//        		e.printStackTrace();
//                return FormValidation.error(e.getMessage());
        	}
        	return items;
        }

        public ListBoxModel doFillClusterRoleIdItems(@QueryParameter String f2cAccessKey,
        		@QueryParameter String f2cSecretKey,
        		@QueryParameter String f2cEndpoint,
        		@QueryParameter String clusterId) {
        	ListBoxModel items = new ListBoxModel();
        	items.add("所有虚机组", "0");
        	try {
        		Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
        		List<ClusterRole> list = fit2CloudClient.getClusterRoles(Long.parseLong(clusterId));
        		if(list != null && list.size() > 0) {
        			for(ClusterRole c : list) {
        				items.add(c.getName(), String.valueOf(c.getId()));
        			}
        		}
        	} catch (Exception e) {
//        		e.printStackTrace();
//                return FormValidation.error(e.getMessage());
        	}
        	return items;
        }

        public ListBoxModel doFillServerIdItems(@QueryParameter String f2cAccessKey,
        		@QueryParameter String f2cSecretKey,
        		@QueryParameter String f2cEndpoint,
        		@QueryParameter String clusterId,
        		@QueryParameter String clusterRoleId) {
        	ListBoxModel items = new ListBoxModel();
        	items.add("所有虚机", "0");
        	try {
        		Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
        		List<Server> list = fit2CloudClient.getServers(Long.parseLong(clusterId), Long.parseLong(clusterRoleId), null, null, null, null, false);
        		if(list != null && list.size() > 0) {
        			for(Server c : list) {
        				items.add(c.getName()+"("+c.getLocalIP()+")", String.valueOf(c.getId()));
        			}
        		}
        	} catch (Exception e) {
//        		e.printStackTrace();
//                return FormValidation.error(e.getMessage());
        	}
        	return items;
        }

        public ListBoxModel doFillContactGroupIdItems(@QueryParameter String f2cAccessKey,
        		@QueryParameter String f2cSecretKey,
        		@QueryParameter String f2cEndpoint) {
        	ListBoxModel items = new ListBoxModel();
        	try {
        		Fit2CloudClient fit2CloudClient = new Fit2CloudClient(f2cAccessKey,f2cSecretKey,f2cEndpoint);
        		List<ContactGroup> list = fit2CloudClient.getContactGroupList(null, null);
        		if(list != null && list.size() > 0) {
        			for(ContactGroup c : list) {
        				items.add(c.getName(), String.valueOf(c.getId()));
        			}
        		}
        	} catch (Exception e) {
//        		e.printStackTrace();
//                return FormValidation.error(e.getMessage());
        	}
        	return items;
        }

        public ListBoxModel doFillDeployStrategyItems() {
            ListBoxModel items = new ListBoxModel();

            List<Map<String,String>> supportRepoList = Utils.getStrategyList();
            for(Map<String,String> repoType : supportRepoList){
                items.add(repoType.get("label"),repoType.get("value"));
            }
            return items;
        }

    }

	public String getF2cEndpoint() {
		return f2cEndpoint;
	}

	public String getF2cAccessKey() {
		return f2cAccessKey;
	}

	public String getF2cSecretKey() {
		return f2cSecretKey;
	}

	public Long getApplicationRepoId() {
		return applicationRepoId;
	}

	public Long getApplicationId() {
		return applicationId;
	}

	public String getApplicationVersion() {
		return applicationVersion;
	}

	public String getIncludes() {
		return includes;
	}

	public String getExcludes() {
		return excludes;
	}

	public String getNexusGroupId() {
		return nexusGroupId;
	}

	public String getNexusArtifactId() {
		return nexusArtifactId;
	}

	public boolean isWaitForCompletion() {
		return waitForCompletion;
	}

	public Long getPollingTimeoutSec() {
		return pollingTimeoutSec;
	}

	public Long getPollingFreqSec() {
		return pollingFreqSec;
	}

	public Long getClusterId() {
		return clusterId;
	}

	public Long getClusterRoleId() {
		return clusterRoleId;
	}

	public Long getServerId() {
		return serverId;
	}

	public Long getContactGroupId() {
		return contactGroupId;
	}

	public String getDeployStrategy() {
		return deployStrategy;
	}

	public boolean isAutoDeploy() {
		return autoDeploy;
	}

	public String getDescription() {
		return description;
	}

	public String getAppspecFilePath() {
		return appspecFilePath;
	}

	public String getArtifactType() {
		return artifactType;
	}

	public String getNexusArtifactVersion() {
		return nexusArtifactVersion;
	}

	private void log(String msg) {
		logger.println(LOG_PREFIX+msg);
	}

	public String getPath() {
		return path;
	}

	public String getObjectPrefixAliyun() {
		return objectPrefixAliyun;
	}

	public String getObjectPrefixAWS() {
		return objectPrefixAWS;
	}

	public boolean isNexusChecked() {
		return nexusChecked;
	}

	public boolean isArtifactoryChecked() {
		return artifactoryChecked;
	}

	public boolean isOssChecked() {
		return ossChecked;
	}

	public boolean isS3Checked() {
		return s3Checked;
	}
}
