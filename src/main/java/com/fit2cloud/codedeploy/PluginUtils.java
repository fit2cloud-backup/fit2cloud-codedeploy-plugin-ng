package com.fit2cloud.codedeploy;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;

import java.io.File;
import java.io.PrintStream;

public class PluginUtils {

	/**
	 * This method will always return the project workspace whether the job
	 * builded on master or slave
	 * 
	 * @param project
	 * @param logger
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static FilePath getProjectWorkspaceOnMaster(AbstractProject project, PrintStream logger) {
		FilePath projectWorkspaceOnMaster;

		// free-style projects
		if (project instanceof FreeStyleProject) {
			FreeStyleProject freeStyleProject = (FreeStyleProject) project;

			// do we use a custom workspace?
			if (freeStyleProject.getCustomWorkspace() != null && freeStyleProject.getCustomWorkspace().length() > 0) {
				projectWorkspaceOnMaster = new FilePath(new File(freeStyleProject.getCustomWorkspace()));
			} else {
				projectWorkspaceOnMaster = new FilePath(new File(freeStyleProject.getRootDir(), "workspace"));
			}
		} else {
			projectWorkspaceOnMaster = new FilePath(new File(project.getRootDir(), "workspace"));
		}

		try {
			// create the workspace if it doesn't exist yet
			projectWorkspaceOnMaster.mkdirs();
		} catch (Exception e) {
			if (logger != null) {
				logger.println("An exception occured while creating " + projectWorkspaceOnMaster.getName() + ": " + e);
			}
			System.out.println("An exception occured while creating " + projectWorkspaceOnMaster.getName());
		}

		return projectWorkspaceOnMaster;
	}
}