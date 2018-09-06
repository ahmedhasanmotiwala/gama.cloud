package ummisco.gama.ui.views.displays;

import static ummisco.gama.ui.bindings.GamaKeyBindings.COMMAND;
import static ummisco.gama.ui.bindings.GamaKeyBindings.format;
import static ummisco.gama.ui.resources.IGamaIcons.DISPLAY_TOOLBAR_SNAPSHOT;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IWorkbenchPage;


import msi.gama.common.interfaces.IGui;
import msi.gama.common.preferences.GamaPreferences;
import msi.gama.core.web.editor.GamaPerspectiveHelper;
import msi.gama.outputs.LayeredDisplayData.Changes;
import msi.gama.outputs.LayeredDisplayData.DisplayDataListener;
import msi.gama.runtime.GAMA;
import ummisco.gama.ui.bindings.GamaKeyBindings;
import ummisco.gama.ui.resources.GamaIcons;
import ummisco.gama.ui.resources.IGamaColors;
import ummisco.gama.ui.resources.IGamaIcons;
import ummisco.gama.ui.utils.WorkbenchHelper;

import ummisco.gama.ui.views.toolbar.GamaCommand;
import ummisco.gama.ui.views.toolbar.GamaToolbar2;
import ummisco.gama.ui.views.toolbar.GamaToolbarFactory;

public class LayeredDisplayDecorator implements DisplayDataListener {

	protected LayeredDisplayMultiListener keyAndMouseListener;
	protected DisplaySurfaceMenu menuManager;
	protected final LayeredDisplayView view;
	ToolItem fs = null;
	protected Composite normalParentOfFullScreenControl;
	int[] sideControlWeights = new int[] { 30, 70 };
	protected Shell fullScreenShell;
	protected Composite sidePanel;
	public DisplayOverlay overlay;
	public GamaToolbar2 toolbar;

	boolean isOverlayTemporaryVisible, sideControlsVisible, interactiveConsoleVisible, simulationControlsVisible;
	protected IPerspectiveListener perspectiveListener;
	final GamaCommand toggleSideControls, toggleOverlay, takeSnapshot, toggleFullScreen, toggleInteractiveConsole,
			runExperiment, stepExperiment, closeExperiment;

	LayeredDisplayDecorator(final LayeredDisplayView view) {
		this.view = view;
		toggleSideControls = new GamaCommand("display.layers2", "Toggle side controls " + format(COMMAND, 'L'),
				e -> toggleSideControls());
		toggleOverlay =
				new GamaCommand("display.overlay2", "Toggle overlay " + format(COMMAND, 'O'), e -> toggleOverlay());
		takeSnapshot =
				new GamaCommand(DISPLAY_TOOLBAR_SNAPSHOT, "Take a snapshot", "Take a snapshot", e -> SnapshotMaker
						.getInstance().doSnapshot(view.getOutput(), view.getDisplaySurface(), view.surfaceComposite));
		toggleFullScreen = new GamaCommand("display.fullscreen2", "Toggle fullscreen ESC", e -> toggleFullScreen());
		toggleInteractiveConsole = new GamaCommand("display.presentation2",
				"Toggle interactive console " + format(COMMAND, 'K'), e -> toggleInteractiveConsole());
		runExperiment = new GamaCommand(IGamaIcons.MENU_RUN_ACTION,
				"Run or pause experiment " + GamaKeyBindings.PLAY_STRING, e -> {
					final Item item = (Item) e.widget;
					if (!GAMA.isPaused()) {
						item.setImage(GamaIcons.create(IGamaIcons.MENU_RUN_ACTION).image());
					} else {
						item.setImage(GamaIcons.create("menu.pause4").image());
					}
					GAMA.startPauseFrontmostExperiment();

				});
		stepExperiment = new GamaCommand("menu.step4", "Step experiment " + GamaKeyBindings.STEP_STRING,
				e -> GAMA.stepFrontmostExperiment());
		closeExperiment = new GamaCommand("toolbar.stop2", "Closes experiment " + GamaKeyBindings.QUIT_STRING,
				e -> new Thread(() -> GAMA.closeAllExperiments(true, false)).start());

	}

	public void toggleFullScreen() {
		if (isFullScreen()) {
			adaptToolbarToFullScreen(false);
			if (interactiveConsoleVisible)
				toggleInteractiveConsole();
			if (simulationControlsVisible)
				toggleSimulationControls();
			view.controlToSetFullScreen().setParent(normalParentOfFullScreenControl);
			createOverlay();
			normalParentOfFullScreenControl.layout(true, true);
			destroyFullScreenShell();
			view.setFocus();
		} else {
			adaptToolbarToFullScreen(true);
			fullScreenShell = createFullScreenShell();
			normalParentOfFullScreenControl = view.controlToSetFullScreen().getParent();
			view.controlToSetFullScreen().setParent(fullScreenShell);
			createOverlay();
			fullScreenShell.layout(true, true);
			fullScreenShell.setVisible(true);
			view.getZoomableControls()[0].forceFocus();
//			if (GamaPreferences.Displays.DISPLAY_TOOLBAR_FULLSCREEN.getValue())
//				toggleSimulationControls();
		}
	}

	private void adaptToolbarToFullScreen(final boolean entering) {
		fs.setImage(GamaIcons.create(entering ? "display.fullscreen3" : "display.fullscreen2").image());

		if (entering) {
			toolbar.button(toggleSideControls, SWT.LEFT);
			toolbar.button(toggleOverlay, SWT.LEFT);
			toolbar.button(toggleInteractiveConsole, SWT.LEFT);
			toolbar.sep(GamaToolbarFactory.TOOLBAR_SEP, SWT.LEFT);
			toolbar.button(runExperiment, SWT.LEFT);
			toolbar.button(stepExperiment, SWT.LEFT);
			toolbar.button(closeExperiment, SWT.LEFT);
		} else
			toolbar.wipe(SWT.LEFT, true);
	}

	public void createOverlay() {
		boolean wasVisible = false;
		if (overlay != null) {
			wasVisible = overlay.isVisible();
			overlay.dispose();
		}
		overlay = new DisplayOverlay(view, view.surfaceComposite, view.getOutput().getOverlayProvider());
		if (wasVisible)
			overlay.setVisible(true);
		overlay.setVisible(GamaPreferences.Displays.CORE_OVERLAY.getValue());
		if (overlay.isVisible()) {
			overlay.update();
		}
	}

	public void createSidePanel(final SashForm form) {

		sidePanel = new Composite(form, SWT.BORDER);
		final GridLayout layout = new GridLayout(1, true);
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		sidePanel.setLayout(layout);
		sidePanel.setBackground(IGamaColors.WHITE.color());
	}

	public void createDecorations(final SashForm form) {
		final LayerSideControls side = new LayerSideControls();
		side.fill(sidePanel, view);
		createOverlay();
		addPerspectiveListener();
		keyAndMouseListener = new LayeredDisplayMultiListener(view.getDisplaySurface(), this);
		menuManager = new DisplaySurfaceMenu(view.getDisplaySurface(), view.getParentComposite(), presentationMenu());
		if (view.getOutput().getData().fullScreen() > -1) {
			toggleFullScreen();
		}
	}

	public void addPerspectiveListener() {
		perspectiveListener = new IPerspectiveListener() {
			boolean previousState = false;

			@Override
			public void perspectiveChanged(final IWorkbenchPage page, final IPerspectiveDescriptor perspective,
					final String changeId) {}

			@Override
			public void perspectiveActivated(final IWorkbenchPage page, final IPerspectiveDescriptor perspective) {
				if (perspective.getId().equals(GamaPerspectiveHelper.PERSPECTIVE_MODELING_ID)) {
					if (view.getOutput() != null && view.getDisplaySurface() != null) {
						if (!GamaPreferences.Displays.CORE_DISPLAY_PERSPECTIVE.getValue()) {
							previousState = view.getOutput().isPaused();
							view.getOutput().setPaused(true);
						}
					}
					if (overlay != null) {
						overlay.hide();
					}
				} else {
					if (!GamaPreferences.Displays.CORE_DISPLAY_PERSPECTIVE.getValue()) {
						if (view.getOutput() != null && view.getDisplaySurface() != null) {
							view.getOutput().setPaused(previousState);
						}
					}
					if (overlay != null) {
						overlay.update();
					}
				}
			}
		};
		WorkbenchHelper.getWindow(RWT.getUISession().getAttribute("user").toString()).addPerspectiveListener(perspectiveListener);
	}

	public boolean isFullScreen() {
		return fullScreenShell != null;
	}

	private Shell createFullScreenShell() {
//		final int monitorId = view.getOutput().getData().fullScreen();
//		return WorkbenchHelper.obtainFullScreenShell(monitorId);
		return fullScreenShell;
	}

	private void destroyFullScreenShell() {
		if (fullScreenShell == null)
			return;
		fullScreenShell.close();
		fullScreenShell.dispose();
		fullScreenShell = null;
	}

	protected Runnable displayOverlay = () -> {
		if (overlay == null) { return; }
		updateOverlay();
	};

	protected void updateOverlay() {
		if (overlay == null)
			return;
		if (view.forceOverlayVisibility()) {
			if (!overlay.isVisible()) {
				isOverlayTemporaryVisible = true;
				overlay.setVisible(true);
			}
		} else {
			if (isOverlayTemporaryVisible) {
				isOverlayTemporaryVisible = false;
				overlay.setVisible(false);
			}
		}
		if (overlay.isVisible())
			overlay.update();

	}

	public void toggleOverlay() {
		overlay.setVisible(!overlay.isVisible());
	}

	public void toggleSideControls() {
		if (sideControlsVisible) {
			sideControlWeights = view.form.getWeights();
			view.form.setMaximizedControl(view.getParentComposite().getParent());
			sideControlsVisible = false;
		} else {
			view.form.setWeights(sideControlWeights);
			view.form.setMaximizedControl(null);
			sideControlsVisible = true;
		}
	}

	public void toggleInteractiveConsole() {
		if (!sideControlsVisible)
			toggleSideControls();
//		final InteractiveConsoleView view =
//				(InteractiveConsoleView) WorkbenchHelper.findView(IGui.INTERACTIVE_CONSOLE_VIEW_ID, null, true);
//		if (view == null)
//			return;
//		if (interactiveConsoleVisible) {
//			view.getControlToDisplayInFullScreen().setParent(view.getParentOfControlToDisplayFullScreen());
//			view.getParentOfControlToDisplayFullScreen().layout();
//			interactiveConsoleVisible = false;
//		} else {
//			view.getControlToDisplayInFullScreen().setParent(sidePanel);
//			interactiveConsoleVisible = true;
//		}
//		sidePanel.layout(true, true);
	}

	Composite tp;

	public void toggleSimulationControls() {
		if (simulationControlsVisible) {
			toolbar.setParent(tp);
			tp = null;
			view.getParentComposite().getParent().layout(true, true);
			simulationControlsVisible = false;
		} else {
			tp = toolbar.getParent();
			toolbar.setParent(view.getParentComposite().getParent());
			simulationControlsVisible = true;
		}
		toolbar.layout(true, true);
		toolbar.getParent().layout();
	}

	private MenuManager presentationMenu() {
		final MenuManager mm = new MenuManager();

		mm.setMenuText("Presentation");
		mm.setImageDescriptor(GamaIcons.create("display.sidebar2").descriptor());
		mm.add(toggleSideControls.toAction());
		mm.add(toggleOverlay.toAction());
		mm.add(new Action("Toggle toolbar " + GamaKeyBindings.format(GamaKeyBindings.COMMAND, 'T'),
				GamaIcons.create("display.fullscreen.toolbar2").descriptor()) {

			@Override
			public boolean isEnabled() {
				return isFullScreen();
			}

			@Override
			public void run() {
				if (isFullScreen())
					toggleSimulationControls();
			}
		});
		return mm;
	}

	public void createToolItems(final GamaToolbar2 tb) {
		toolbar = tb;
		tb.sep(GamaToolbarFactory.TOOLBAR_SEP, SWT.RIGHT);
		tb.button(takeSnapshot, SWT.RIGHT);
		fs = tb.button(toggleFullScreen, SWT.RIGHT);
		tb.sep(GamaToolbarFactory.TOOLBAR_SEP, SWT.RIGHT);
		tb.menu(IGamaIcons.MENU_POPULATION, "Browse displayed agents by layers", "Browse through all displayed agents",
				trigger -> menuManager.buildToolbarMenu(trigger, (ToolItem) trigger.widget), SWT.RIGHT);
	}

	public void dispose() {
		// FIXME Remove the listeners
		WorkbenchHelper.getWindow(RWT.getUISession().getAttribute("user").toString()).removePerspectiveListener(perspectiveListener);
		if (keyAndMouseListener != null) {
//			keyAndMouseListener.dispose();
		}
		if (overlay != null) {
			overlay.close();
		}

		if (menuManager != null) {
			menuManager.disposeMenu();
		}
		menuManager = null;
		toolbar = null;
		fs = null;
		tp = null;
		sidePanel = null;
		normalParentOfFullScreenControl = null;
		if (fullScreenShell != null && !fullScreenShell.isDisposed()) {
			fullScreenShell.dispose();
		}
		fullScreenShell = null;
	}

	@Override
	public void changed(final Changes changes, final Object value) {
		switch (changes) {
			case ZOOM:
				WorkbenchHelper.asyncRun(RWT.getUISession().getAttribute("user").toString(),() -> updateOverlay());
				break;
			default:
				break;
		}

	}

}
