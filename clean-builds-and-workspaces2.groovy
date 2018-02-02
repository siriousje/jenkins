import hudson.node_monitors.*
import hudson.slaves.*

// globals  
now = Calendar.instance;
cleanAfter = 24 * 60 * 60; // one day
jenkins = Jenkins.instance;
workspace = new FilePath(jenkins.rootPath, "workspace");


def List<Item> items = jenkins.getAllItems();

for (item in items) {
  processItem(0, item)
}

def isOldBuild(build) {
  return (now.time.time/1000) - (build.time.time/1000) > cleanAfter;
}

def processItem(depth, item) {
  if (item.class.canonicalName != null												// skip objects that have class :-)
      && item.class.canonicalName != "com.cloudbees.hudson.plugins.folder.Folder"	// skip folders, lists etc as the items will be in the list already
      && item.class.canonicalName != "hudson.maven.MavenModuleSet"
      && item.class.canonicalName != "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject"	
      && item.class.canonicalName != "com.github.mjdetullio.jenkins.plugins.multibranch.MavenMultiBranchProject"
      && item.class.canonicalName != "hudson.model.FreeStyleProject" 				// not sure what to do there
     ) {
    if (item.class.canonicalName == "org.jenkinsci.plugins.workflow.job.WorkflowJob") {
      log depth, "checking builds for $item.fullName"
      processBuildsForWorkflowItem(depth+1, item)
      // we don't have multiple nodes, so this is fine
      def workspace = jenkins.getWorkspaceFor(item)
      if (workspace.length() >= 4096 && workspace.isDirectory() && isPomButNotNodeProject(workspace)) {
        if (item.builds.isEmpty()) {
          log depth+1, "deleting pom project workspace $workspace.name"
          for (dir in workspace.list()) {
            if (dir.isDirectory()) {
              dir.deleteRecursive();
            }
            else {
              dir.delete();
            }
          }
        }
      }
    }
    else if (item.class.canonicalName == "hudson.maven.MavenModuleSet") {
      log depth, "checking MavenModuleSet $item.fullName"
    }
    else if (item.class.canonicalName == "hudson.maven.MavenModule") {
      if (item.isBuilding()) {
      	log depth, "maven module $item.fullName is building, skipping"
      }
      else {
        log depth, "clearing workspace for maven module $item.fullName" 
        item.doDoWipeOutWorkspace()
        processBuildsForWorkflowItem(depth+1, item)
      }
    }
    else {
      log depth, item.fullName + " -> " + item.class
    }

  }
  return "";
}

// helps with some indenting on the output
def log(i, msg) {
  println "    ".multiply(i) + msg
}

def processBuildsForWorkflowItem(depth, item) {
  if (item.builds.isEmpty()) {
    log depth, "no builds to delete"
  }
  else {
    for (build in item.builds) {
      if (build.isBuilding()) {
        log depth, "won't be deleting build $build.number as it is currently building"
      } 
      else if (isOldBuild(build)) {
        log depth, "found build $build.number done at $build.time, which is old and can be deleted"
        build.deleteArtifacts() // probably also happens on build.delete, but perhaps later we just want the artifacts wiped
        build.delete()
      }
      else {
        log depth, "found build $build.number done at $build.time, which is too young to be deleted"
        //log depth, build.getExecution()
      }
    }
  }
}

// @tmp and @script @12 are all subfolders we don't care about since we'll be wiping them when we clean the parent
def isAtFolder(dir) {
  return dir ==~ /.*@(tmp|script|\d+)$/;
}

def isPomButNotNodeProject(ws) {
  def isPom = false;
  
  for (path in ws.list()) {
    if (path.isDirectory() && path.name == "node_modules") {
      return false;
    }
    else if (!path.isDirectory() && path.name == "pom.xml") {
      isPom = true;
    }
  }
  return isPom;
}



return null
