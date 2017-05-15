/*********************************************************************************************
 *
 * 'UserControlView.java, in plugin ummisco.gama.ui.experiment, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.web.ui.views.user;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolItem;

import msi.gama.common.interfaces.IGamaView;
import msi.gama.common.interfaces.IGui;
import msi.gama.lang.gaml.web.editor.GAMAHelper;
import msi.gama.lang.gaml.web.ui.controls.FlatButton;
import msi.gama.lang.gaml.web.ui.parameters.EditorFactory;
import msi.gama.lang.gaml.web.ui.resources.GamaColors;
import msi.gama.lang.gaml.web.ui.resources.GamaColors.GamaUIColor;
import msi.gama.lang.gaml.web.ui.resources.GamaIcons;
import msi.gama.lang.gaml.web.ui.resources.IGamaColors;
import msi.gama.lang.gaml.web.ui.resources.IGamaIcons;
import msi.gama.lang.gaml.web.ui.utils.WorkbenchHelper;
import msi.gama.lang.gaml.web.ui.views.GamaViewPart;
import msi.gama.lang.gaml.web.ui.views.toolbar.GamaToolbar2;
import msi.gama.runtime.IScope;
import msi.gaml.architecture.user.UserInputStatement;
import msi.gaml.architecture.user.UserPanelStatement;
import msi.gaml.statements.IStatement;
import msi.gaml.statements.UserCommandStatement;

public class UserControlView extends GamaViewPart implements IGamaView.User {

	public static String ID = IGui.USER_CONTROL_VIEW_ID;

	private IScope scope;
	UserPanelStatement panel;
	private Composite body;
	ToolItem inspectItem, continueItem;

	@Override
	public void initFor(final IScope scope, final UserPanelStatement panel) {
		this.panel = panel;
		this.scope = scope;

		if (body != null && !body.isDisposed()) {
			body.dispose();
			body = null;
		}

		ownCreatePartControl(parent);
		parent.layout();
	}

	private void deactivate(final Composite parent) {
		for (final Control c : parent.getChildren()) {
			if (c instanceof Composite) {
				deactivate((Composite) c);
			} else {
				c.setEnabled(false);
			}
		}
	}

	@Override
	public void ownCreatePartControl(final Composite parent) {
		parent.setBackground(IGamaColors.WHITE.color());
		if (scope == null) {
			return;
		}
		inspectItem.setEnabled(true);
		continueItem.setEnabled(true);
		setPartName(
				"[" + scope.getAgent().getName() + " in " + scope.getSimulation().getName() + "] " + panel.getName());
		parent.setLayout(new FillLayout());
		parent.setBackground(IGamaColors.WHITE.color());
		toolbar.status((Image) null,
				"User control, agent " + scope.getAgent().getName() + ", cycle " + scope.getClock().getCycle(),
				IGamaColors.NEUTRAL, SWT.LEFT);
		body = new Composite(parent, SWT.None);
		GridLayout layout = new GridLayout(3, false);
		body.setLayout(layout);
		body.setBackground(IGamaColors.WHITE.color());
		for (final IStatement statement : panel.getUserCommands()) {
			if (statement instanceof UserCommandStatement) {
				final UserCommandStatement c = (UserCommandStatement) statement;
				final Composite commandComposite = new Composite(body, SWT.BORDER);
				final GridData data = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
				commandComposite.setLayoutData(data);
				layout = new GridLayout(3, false);
				commandComposite.setLayout(layout);
				commandComposite.setBackground(IGamaColors.WHITE.color());
				final List<UserInputStatement> inputs = c.getInputs();
				final int nbLines = inputs.size() > 1 ? inputs.size() : 1;
				final int nbCol = inputs.size() > 0 ? 1 : 3;
				GamaUIColor color = GamaColors.get(c.getColor(scope));
				if (color == null)
					color = IGamaColors.BLUE;
				final Image image = GamaIcons.create(c.isContinue(scope) ? "small.continue" : "small.run").image();
				final FlatButton b = FlatButton.button(commandComposite, color, c.getName(), image);
				b.setEnabled(c.isEnabled(scope));
				final GridData gd = new GridData(SWT.LEFT, SWT.CENTER, true, true, nbCol, nbLines);
				b.setLayoutData(gd);
				b.addSelectionListener(new SelectionAdapter() {

					@Override
					public void widgetSelected(final SelectionEvent e) {
						c.executeOn(scope);
						GAMAHelper.getExperiment().refreshAllOutputs();
						if (c.isContinue(scope)) {
							doContinue();
						}
					}

				});
				for (final UserInputStatement i : inputs) {
					scope.addVarWithValue(i.getTempVarName(), i.value(scope));
					EditorFactory.create(scope, commandComposite, i, newValue -> {
						i.setValue(scope, newValue);
						i.executeOn(scope);
					}, false, false);
				}

			}
		}

	}

	protected void doContinue() {
		scope.setOnUserHold(false);
		deactivate(parent);
		WorkbenchHelper.hideView(this);
	}

	@Override
	public void widgetDisposed(final DisposeEvent e) {
		scope.setOnUserHold(false);
		super.widgetDisposed(e);
	}

	@Override
	protected GamaUIJob createUpdateJob() {
		return new GamaUIJob() {

			@Override
			protected UpdatePriority jobPriority() {
				return UpdatePriority.HIGH;
			}

			@Override
			public IStatus runInUIThread(final IProgressMonitor monitor) {
				initFor(scope, panel);
				return Status.OK_STATUS;
			}
		};
	}

	/**
	 * Method createToolItem()
	 * 
	 * @see msi.gama.lang.gaml.web.ui.views.toolbar.IToolbarDecoratedView#createToolItem(int,
	 *      msi.gama.lang.gaml.web.ui.views.toolbar.GamaToolbar2)
	 */
	@Override
	public void createToolItems(final GamaToolbar2 tb) {
		super.createToolItems(tb);

		inspectItem = tb.button(IGamaIcons.PANEL_INSPECT, "Inspect", "Inspect", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				scope.getGui().setSelectedAgent(scope.getAgent());
			}

		}, SWT.RIGHT);
		inspectItem.setEnabled(false);
		continueItem = tb.button(IGamaIcons.PANEL_CONTINUE, "Continue", "Continue", new SelectionListener() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				doContinue();
			}

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				widgetSelected(e);
			}

		}, SWT.RIGHT);
		continueItem.setEnabled(false);

	}

	@Override
	protected boolean needsOutput() {
		return false;
	}

}
