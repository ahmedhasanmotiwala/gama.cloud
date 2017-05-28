/*********************************************************************************************
 *
 * 'SimulationStateProvider.java, in plugin ummisco.gama.ui.experiment, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.web.ui.commands;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.rap.rwt.RWT;
import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;

import msi.gama.common.interfaces.IGui;
import msi.gama.kernel.experiment.IExperimentPlan;
import msi.gama.lang.gaml.web.editor.GAMAHelper;
import msi.gama.runtime.ISimulationStateProvider;

public class SimulationStateProvider extends AbstractSourceProvider implements ISimulationStateProvider {

	public final static String SIMULATION_RUNNING_STATE = "msi.gama.lang.gaml.web.ui.experiment.SimulationRunningState";
	public final static String SIMULATION_TYPE = "msi.gama.lang.gaml.web.ui.experiment.SimulationType";
	public final static String SIMULATION_STEPBACK = "msi.gama.lang.gaml.web.ui.experiment.SimulationStepBack";

	private final static Map<String, String> map = new HashMap<>(3);

	@Override
	public void dispose() {
		System.out.println("dispose SIMULATION_RUNNING_STATE");
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] { SIMULATION_RUNNING_STATE, SIMULATION_TYPE, SIMULATION_STEPBACK };
	}

	@Override
	public Map<String, String> getCurrentState() {
		String uid = RWT.getUISession().getAttribute("user").toString();
		final String state = GAMAHelper.getGui().getExperimentState(uid);
		final IExperimentPlan exp = GAMAHelper.getExperiment();
		final String type = exp == null ? IGui.NONE
				: exp.isBatch() ? "BATCH" : exp.isMemorize() ? "MEMORIZE" : "REGULAR";
		map.put(SIMULATION_RUNNING_STATE, state);
		map.put(SIMULATION_TYPE, type);
		map.put(SIMULATION_STEPBACK, "CANNOT_STEP_BACK");
		return map;
	}

	/**
	 * Change the UI state based on the state of the simulation (none, stopped,
	 * running or notready)
	 */
	@Override
	public void updateStateTo(final String state) {
		fireSourceChanged(ISources.WORKBENCH, SIMULATION_RUNNING_STATE, state);
		final IExperimentPlan exp = GAMAHelper.getExperiment();
		final String type = exp == null ? "NONE" : exp.isBatch() ? "BATCH" : exp.isMemorize() ? "MEMORIZE" : "REGULAR";
		fireSourceChanged(ISources.WORKBENCH, SIMULATION_TYPE, type);

		String canStepBack = "CANNOT_STEP_BACK";

		if (exp != null) {
			if (exp.getAgent() != null) {
				canStepBack = exp.getAgent().canStepBack() ? "CAN_STEP_BACK" : "CANNOT_STEP_BACK";
			}
		}

		fireSourceChanged(ISources.WORKBENCH, SIMULATION_STEPBACK, canStepBack);

	}

}