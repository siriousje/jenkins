now = Calendar.instance;
cleanAfter = 24 * 60 * 60; // one day


cleanWorkspace(Hudson.instance.items)

def isOldBuild(build) {
  return (now.time.time/1000) - (build.time.time/1000) > cleanAfter;
}
 
def cleanWorkspace (items) {
  for (item in items) {
    if (item.class.canonicalName == null) { 
      println("$item name cannot be cleaned since it's not an object")
    }
    else if (item.class.canonicalName == "com.cloudbees.hudson.plugins.folder.Folder") {
      println("  $item.name is a folder, recursing into subfolders")
      cleanWorkspace(item.items);
    } 
    else if (item.class.canonicalName == "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject"
        || item.class.canonicalName == "com.github.mjdetullio.jenkins.plugins.multibranch.MavenMultiBranchProject") {
      println("  $item.name is a "+ item.class.canonicalName + ", recursing into each branch")
      for (subitem in item.items) {
        if (subitem.class.canonicalName == "org.jenkinsci.plugins.workflow.job.WorkflowJob") {
          println("    $subitem.fullName is a workflow job");
          processBuildsForWorkflowItem(subitem, true); // get rid of all builds for all branches
        }
        else {
          println("    unknown subitem: $subitem");
        }
      }        
    }
    else if (item.isBuilding()) { 
      println("  skipping job $item.name, currently building")
    }
    else if (item.class.canonicalName == "org.jenkinsci.plugins.workflow.job.WorkflowJob") {
      println("  $item.name is a workflow job, cleaning builds");
      processBuildsForWorkflowItem(item, false); // keep young builds
    } 
    else if (item.class.canonicalName != "hudson.model.ExternalJob") { // anything else should have the wipeout
      println("wiping workspace for item $item.name " + " (" + item.class.canonicalName+")")
      item.doDoWipeOutWorkspace()
    } 
    else {
        println("  Item of type " + item.class.canonicalName + " cannot have its workspace cleaned")
    }
  }
}

def processBuildsForWorkflowItem(item, deleteAllBranches) {
  if (item.class.canonicalName != "org.jenkinsci.plugins.workflow.job.WorkflowJob") {
    println("      this is not a WorkflowJob, cannot process builds")
  }
  else if (item.builds.isEmpty()) {
    println("      no builds to delete")
  }
  else {
    for (build in item.builds) {
      if (build.isBuilding()) {
        println("     won't be deleting build $build.number as it is currently building");
      } 
      else if (deleteAllBranches || isOldBuild(build)) {
        println("     found build $build.number done at $build.time, which is old and can be deleted")
        build.deleteArtifacts() // probably also happens on build.delete, but perhaps later we just want the artifacts wiped
        build.delete()
      }
      else {
        println("     found build $build.number done at $build.time, which is too young to be deleted")
      }
    }
  }
}