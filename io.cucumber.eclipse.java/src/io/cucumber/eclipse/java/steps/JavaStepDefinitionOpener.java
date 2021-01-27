package io.cucumber.eclipse.java.steps;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.osgi.service.component.annotations.Component;

import io.cucumber.eclipse.editor.hyperlinks.IStepDefinitionOpener;
import io.cucumber.eclipse.java.JDTUtil;
import io.cucumber.eclipse.java.plugins.CucumberCodeLocation;
import io.cucumber.eclipse.java.plugins.MatchedPickleStep;
import io.cucumber.eclipse.java.plugins.MatchedStep;
import io.cucumber.eclipse.java.validation.CucumberGlueValidator;
import io.cucumber.messages.Messages.GherkinDocument.Feature.Step;

@Component(service = IStepDefinitionOpener.class)
public class JavaStepDefinitionOpener implements IStepDefinitionOpener {

	public static void showMethod(IMethod[] methods, Shell shell) {
		if (methods == null || methods.length == 0) {
			return;
		}
		try {
			if (methods.length == 1) {
				open(methods[0]);
			}
		} catch (PartInitException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void open(IMethod method) throws PartInitException, JavaModelException {
		ICompilationUnit cu = method.getCompilationUnit();
		IEditorPart javaEditor = JavaUI.openInEditor(cu);
		JavaUI.revealInEditor(javaEditor, (IJavaElement) method);
	}

	@Override
	public boolean openInEditor(ITextViewer textViewer, IResource resource, Step step) throws CoreException {
		IJavaProject project = JDTUtil.getJavaProject(resource);
		if (project == null) {
			return false;
		}
		AtomicReference<IMethod[]> resolvedMethods = new AtomicReference<>();
		Display display = textViewer.getTextWidget().getDisplay();
		BusyIndicator.showWhile(display, () -> {
			AtomicBoolean done = new AtomicBoolean();
			Job job = Job.create("Search for step '" + step.getText() + "'", new ICoreRunnable() {

				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					try {
						Collection<MatchedStep<?>> steps = CucumberGlueValidator
								.getMatchedSteps(textViewer.getDocument(), monitor);

						CucumberCodeLocation location = steps.stream()
								.filter(matched -> step.getLocation().getLine() == matched.getLocation().getLine())
								.filter(MatchedPickleStep.class::isInstance).map(MatchedPickleStep.class::cast)
								.filter(matched -> matched.getTestStep().getStep().getText()
										.equalsIgnoreCase(step.getText()))
								.map(matched -> matched.getCodeLocation()).findFirst().orElse(null);
						if (location != null) {
							resolvedMethods.set(JDTUtil.resolveMethod(project, location, monitor));
						}
					} catch (InterruptedException e) {
					} finally {
						done.set(true);
					}
				}
			});
			job.schedule();
			while (!done.get() && !display.isDisposed()) {
				if (!display.readAndDispatch())
					display.sleep();
			}
		});
		IMethod[] method = resolvedMethods.get();
		if (method != null) {
			showMethod(method, textViewer.getTextWidget().getShell());
		}
		return method != null && method.length > 0;
	}

	@Override
	public boolean canOpen(IResource resource) throws CoreException {
		return JDTUtil.getJavaProject(resource) != null;
	}

}
