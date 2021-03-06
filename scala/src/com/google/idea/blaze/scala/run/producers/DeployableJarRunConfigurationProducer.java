/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.scala.run.producers;

import static com.google.idea.blaze.scala.run.producers.BlazeScalaMainClassRunConfigurationProducer.getMainObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;

class DeployableJarRunConfigurationProducer
    extends BlazeRunConfigurationProducer<ApplicationConfiguration> {

  static final Key<Label> TARGET_LABEL = Key.create("blaze.scala.library.target.label");

  DeployableJarRunConfigurationProducer() {
    super(ApplicationConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      ApplicationConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    TargetIdeInfo target = findTarget(context.getProject(), mainObject);
    if (target == null) {
      return false;
    }

    Label label = target.key.label;
    File jarFile = getDeployJarFile(label, context.getProject());
    if (jarFile == null) {
      return false;
    }

    configuration.setVMParameters("-cp " + jarFile.getPath());
    configuration.setMainClassName(mainObject.getTruncedQualifiedName());
    configuration.setModule(context.getModule());

    configuration.putUserData(TARGET_LABEL, label);
    configuration.setName(mainObject.name());
    configuration.setNameChangedByUser(true); // don't revert to generated name

    setDeployableJarGeneratorTask(configuration);

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      ApplicationConfiguration configuration, ConfigurationContext context) {
    PsiClass mainClass = configuration.getMainClass();
    if (mainClass == null || mainClass.getQualifiedName() == null) {
      return false;
    }
    if (configuration.getVMParameters() == null) {
      return false;
    }
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    TargetIdeInfo target = findTarget(context.getProject(), mainObject);
    if (target == null) {
      return false;
    }
    Label label = target.key.label;
    File jarFile = getDeployJarFile(label, context.getProject());
    if (jarFile == null) {
      return false;
    }
    return mainClass.getQualifiedName().equals(mainObject.getTruncedQualifiedName())
        && configuration.getVMParameters().contains("-cp " + jarFile.getPath());
  }

  @Nullable
  private static TargetIdeInfo findTarget(Project project, ScObject mainObject) {
    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return null;
    }

    Collection<TargetIdeInfo> targets =
        BlazeScalaMainClassRunConfigurationProducer.findScalaBinaryTargets(project, mainObjectFile);
    return Iterables.getFirst(targets, null);
  }

  private void setDeployableJarGeneratorTask(ApplicationConfiguration config) {
    Project project = config.getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    runManager.setBeforeRunTasks(
        config, ImmutableList.of(new GenerateExecutableDeployableJarProviderTaskProvider.Task()));
  }

  @Nullable
  private File getDeployJarFile(Label target, Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    File blazeBin = projectData.blazeInfo.getBlazeBinDirectory();
    return new File(blazeBin, String.format("%s_deploy.jar", target.targetName()));
  }
}
