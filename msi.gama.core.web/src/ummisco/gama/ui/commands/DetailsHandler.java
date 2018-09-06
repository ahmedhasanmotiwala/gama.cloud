package ummisco.gama.ui.commands;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.internal.AbstractEnabledHandler;

import ummisco.gama.ui.utils.WorkbenchHelper;

public class DetailsHandler extends AbstractEnabledHandler implements IHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final Command c = WorkbenchHelper.getService(RWT.getUISession().getAttribute("user").toString(),ICommandService.class)
				.getCommand("org.eclipse.equinox.p2.ui.sdk.installationDetails");
		if (c != null) {
			try {
				return c.executeWithChecks(event);
			} catch (NotDefinedException | NotEnabledException | NotHandledException e) {}
		}
		return null;
	}

}
