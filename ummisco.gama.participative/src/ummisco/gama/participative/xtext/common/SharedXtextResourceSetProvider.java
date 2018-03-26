package ummisco.gama.participative.xtext.common;

import java.io.File;

import ummisco.gama.participative.xtext.common.XtextResourceSetProvider;
import org.dslforge.xtext.common.registry.LanguageRegistry;

import org.eclipse.emf.ecore.resource.ResourceSet;

public class SharedXtextResourceSetProvider extends XtextResourceSetProvider {

	@Override
	public ResourceSet get(File project) {
		return LanguageRegistry.INSTANCE.getDefaultResourceSet(FileUtil.getFileExtension(project));
	}
}
