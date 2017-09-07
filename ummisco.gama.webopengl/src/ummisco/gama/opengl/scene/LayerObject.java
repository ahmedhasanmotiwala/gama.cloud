/*********************************************************************************************
 *
 * 'LayerObject.java, in plugin ummisco.gama.opengl, is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl.scene;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.jogamp.opengl.GL2;
import com.vividsolutions.jts.geom.Geometry;

import msi.gama.common.geometry.Scaling3D;
import msi.gama.common.interfaces.ILayer;
import msi.gama.metamodel.shape.GamaPoint;
import msi.gama.metamodel.shape.IShape;
import msi.gama.outputs.layers.OverlayLayer;
import msi.gama.util.file.GamaGeometryFile;
import msi.gaml.statements.draw.DrawingAttributes;
import msi.gaml.statements.draw.FieldDrawingAttributes;
import msi.gaml.types.GamaGeometryType;
import ummisco.gama.modernOpenGL.DrawingEntity;
import ummisco.gama.opengl.Abstract3DRenderer;
import ummisco.gama.opengl.ModernRenderer;
import ummisco.gama.opengl.WebGL2;
import ummisco.gama.opengl.scene.GeometryObject.GeometryObjectWithAnimation;

/**
 * Class LayerObject.
 *
 * @author drogoul
 * @since 3 mars 2014
 *
 */
@SuppressWarnings ({ "rawtypes", "unchecked" })
public class LayerObject {

	final static GamaPoint NULL_OFFSET = new GamaPoint();
	final static GamaPoint NULL_SCALE = new GamaPoint(1, 1, 1);

	private boolean sceneIsInitialized = false;
	protected boolean constantRedrawnLayer = false;

	GamaPoint offset = new GamaPoint(NULL_OFFSET);
	GamaPoint scale = new GamaPoint(NULL_SCALE);
	Double alpha = 1d;
	final ILayer layer;
	volatile boolean isInvalid;
	volatile boolean overlay;
	volatile boolean locked;
	boolean isAnimated;
	final Abstract3DRenderer renderer;
	final LinkedList<List<AbstractObject>> objects;
	List<AbstractObject> currentList;
	Integer openGLListIndex;
	boolean isFading;

	public LayerObject(final Abstract3DRenderer renderer, final ILayer layer) {
		this.renderer = renderer;
		this.layer = layer;
		this.overlay = layer != null && layer.isOverlay();
		currentList = newCurrentList();
		if (layer != null && layer.getTrace() != null || renderer instanceof ModernRenderer) {
			objects = new LinkedList();
			objects.add(currentList);
		} else
			objects = null;
	}

	public boolean isLightInteraction() {
		return true;
	}

	private List newCurrentList() {
		return /* Collections.synchronizedList( */new ArrayList()/* ) */;
	}

	protected boolean isPickable() {
		return layer == null ? false : layer.isSelectable();
	}

	public void draw(final WebOpenGL gl) {
		if (isInvalid()) { return; }
		if (renderer.useShader()) {
			drawWithShader(gl.getGL());
		} else {
			drawWithoutShader(gl);
		}
	}

	private void drawWithShader(final WebGL2 webGL2) {
		final ModernRenderer renderer = (ModernRenderer) this.renderer;

		if (isOverlay()) {
			webGL2.glDisable(GL2.GL_DEPTH_TEST);
		} else {
			webGL2.glEnable(GL2.GL_DEPTH_TEST);
		}

		if (!sceneIsInitialized || constantRedrawnLayer) {
			renderer.getDrawer().prepareMapForLayer(this);
			double alpha = 0d;
			final double originalAlpha = this.alpha;
			final int size = objects.size();
			final double delta = size == 0 ? 0 : 1d / size;
			for (final List<AbstractObject> list : objects) {
				alpha = isFading ? originalAlpha * (alpha + delta) : originalAlpha;
				synchronized (list) {
					for (final AbstractObject object : list) {
						final double alpha1 = alpha;
						renderer.getOpenGLHelper().setCurrentObjectAlpha(alpha1);
						final DrawingEntity[] drawingEntity = renderer.getDrawingEntityGenerator()
								.generateDrawingEntities(renderer.getSurface().getScope(), object, this, webGL2);
						if (overlay) {
							for (final DrawingEntity de : drawingEntity) {
								de.enableOverlay(true);
							}
						}
						if (drawingEntity != null)
							renderer.getDrawer().addDrawingEntities(drawingEntity);
					}
				}
			}
			renderer.getDrawer().redraw();
			sceneIsInitialized = true;
		} else {
			renderer.getDrawer().refresh(this);
		}

	}

	private void drawWithoutShader(final WebOpenGL gl) {
		final GamaPoint scale = getScale();

		final double oldZIncrement = gl.getZIncrement();
		if (overlay) {
			gl.getGL().glDisable(GL2.GL_DEPTH_TEST);
		} else {
			gl.getGL().glEnable(GL2.GL_DEPTH_TEST);
		}
		if (overlay) {
			gl.setZIncrement(0);
			//
			final double viewHeight = gl.getViewHeight();
			final double viewWidth = gl.getViewWidth();
			final double viewRatio = viewWidth / (viewHeight == 0 ? 1 : viewHeight);
			final double worldHeight = gl.getWorldHeight();
			final double worldWidth = gl.getWorldWidth();
			final double maxDim = worldHeight > worldWidth ? worldHeight : worldWidth;
			//
			// final double worldRatio = worldWidth / worldHeight;
			// final double widthRatio = viewWidth / worldWidth;
			// final double heightRatio = viewHeight / worldHeight;
			//
			// final double x, y;
			// double x_scale = 1, y_scale = 1;
			// if (viewRatio >= 1) {
			// if (worldRatio >= 1) {
			// x_scale = worldRatio / viewRatio;
			// y_scale = viewRatio / worldRatio;
			// } else {
			// x_scale = viewRatio / worldRatio;
			// y_scale = worldRatio / viewRatio;
			// ;
			// }
			// } else {
			// if (worldRatio >= 1) {
			// x_scale = worldRatio / viewRatio;
			//
			// y_scale = viewRatio / worldRatio;
			// } else {
			// x_scale = viewRatio / worldRatio;
			// y_scale = worldRatio / viewRatio;
			//
			// }
			//
			// }

			gl.pushIdentity(GL2.GL_PROJECTION);
			// gl.getGL().glOrtho(0, worldWidth, -worldHeight, 0, -1, 1);
			if (viewRatio >= 1.0) {
				gl.getGL().glOrtho(0, maxDim * viewRatio, -maxDim, 0, -1, 1);
			} else {
				gl.getGL().glOrtho(0, maxDim, -maxDim / viewRatio, 0, -1, 1);
			}
			// System.out.println("View ratio " + viewRatio + " World ratio " + worldRatio + " / X Scale " + x_scale
			// + " Y Scale " + y_scale);
			// y_scale = 1;
			// scale.setLocation(x_scale, y_scale, 1);

			gl.pushIdentity(GL2.GL_MODELVIEW);
		}
		try {
			gl.pushMatrix();
			final GamaPoint offset = getOffset();
			// if (overlay)
			// System.out.println("OFFSET: " + offset);
			gl.translateBy(offset.x, -offset.y, overlay ? 0 : offset.z);
			// scale.setLocation(0.5, 0.5, 1);
			// if (overlay)
			// gl.scaleBy(0.5, 0.5, 1);
			// else
			gl.scaleBy(scale.x, scale.y, scale.z);

			final boolean picking = renderer.getPickingState().isPicking() && isPickable();
			if (picking) {
				if (!overlay)
					gl.runWithNames(() -> drawAllObjects(gl, true));
			} else {
				if (isAnimated || overlay) {
					drawAllObjects(gl, false);
				} else {
					if (openGLListIndex == null) {
						openGLListIndex = gl.compileAsList(() -> drawAllObjects(gl, false));
					}
					gl.drawList(openGLListIndex);
				}
			}
		} finally {
			gl.popMatrix();
			gl.setZIncrement(oldZIncrement);
			if (overlay) {
				gl.pop(GL2.GL_MODELVIEW);
				gl.pop(GL2.GL_PROJECTION);
			}
		}

	}

	private void addFrame(final WebOpenGL gl) {
		final double width = layer.getDefinition().getBox().getSize().getX();
		final double height = layer.getDefinition().getBox().getSize().getY();

		gl.pushMatrix();
		gl.translateBy(offset.x, -offset.y - height, 0);
		gl.scaleBy(width, height, 1);
		gl.setCurrentColor(((OverlayLayer) layer).getBackground());
		gl.setCurrentObjectAlpha(((OverlayLayer) layer).getDefinition().getTransparency());
		gl.drawCachedGeometry(IShape.Type.ROUNDED, null);
		gl.popMatrix();
		gl.translateBy(offset.x, -offset.y, 0);
		// gl.scaleBy(0.5, 0.5, 1);
		// gl.setLayerScalingFactor(overlay ? 2f : 1f);

	}

	protected void drawAllObjects(final WebOpenGL gl, final boolean picking) {
		if (overlay) {
			addFrame(gl);
		}
		if (objects != null) {
			double delta = 0;
			if (isFading) {
				final int size = objects.size();
				delta = size == 0 ? 0 : 1d / size;
			}
			double alpha = 0d;
			for (final List<AbstractObject> list : objects) {
				alpha = delta == 0d ? this.alpha : this.alpha * (alpha + delta);
				drawObjects(gl, list, alpha, picking);
			}
		} else
			drawObjects(gl, currentList, alpha, picking);
	}

	protected void drawObjects(final WebOpenGL gl, final List<AbstractObject> list, final double alpha,
			final boolean picking) {
		gl.setCurrentObjectAlpha(alpha);
		for (final AbstractObject object : list) {
			object.draw(gl, renderer.getDrawerFor(object.getDrawerType()), picking);
		}
	}

	public boolean isStatic() {
		if (layer == null) { return true; }
		return !layer.isDynamic();
	}

	public void setAlpha(final Double a) {
		alpha = a;
	}

	public GamaPoint getOffset() {
		return offset == null ? NULL_OFFSET : offset;
	}

	public void setOffset(final GamaPoint offset) {
		this.offset = offset;
	}

	public GamaPoint getScale() {
		return scale == null ? NULL_SCALE : scale;
	}

	public Double getAlpha() {
		return alpha;
	}

	public void setScale(final GamaPoint scale) {
		this.scale.setLocation(scale);
		;
	}

	public StringObject addString(final String string, final DrawingAttributes attributes) {
		final StringObject object = new StringObject(string, attributes);
		currentList.add(object);
		return object;
	}

	public ResourceObject addFile(final GamaGeometryFile file, final DrawingAttributes attributes) {
		final ResourceObject resource = new ResourceObject(file, attributes);
		currentList.add(resource);
		return resource;
	}

	public GeometryObject addImage(final Object o, final DrawingAttributes attributes) {
		// If no dimensions have been defined, then the image is considered as wide and tall as the environment
		Scaling3D size = attributes.getSize();
		if (size == null) {
			size = Scaling3D.of(renderer.getWorldsDimensions());
			attributes.setSize(size);
		}
		final GamaPoint loc = attributes.getLocation();
		final Scaling3D inc = attributes.getSize().dividedBy(2);
		final GamaPoint newLoc = loc == null ? inc.toGamaPoint() : loc.plus(inc.getX(), inc.getY(), inc.getZ());
		// We build a rectangle that will serve as a "support" for the image (which will become its texture)
		final Geometry geometry = GamaGeometryType.buildRectangle(size.getX(), size.getY(), newLoc).getInnerGeometry();

		attributes.setLocation(newLoc);
		attributes.setTexture(o);
		return addGeometry(geometry, attributes);
	}

	public FieldObject addField(final double[] fieldValues, final FieldDrawingAttributes attributes) {
		final FieldObject field = new FieldObject(fieldValues, attributes);
		currentList.add(field);
		return field;
	}

	public GeometryObject addGeometry(final Geometry geometry, final DrawingAttributes attributes) {
		final GeometryObject geom;
		if (attributes.isAnimated()) {
			isAnimated = true;
			geom = new GeometryObjectWithAnimation(geometry, attributes);
		} else {
			geom = new GeometryObject(geometry, attributes);
		}
		currentList.add(geom);
		return geom;
	}

	private int getTrace() {
		if (layer == null) { return 0; }
		final Integer trace = layer.getTrace();
		return trace == null ? 0 : trace;
	}

	private boolean getFading() {
		if (layer == null) { return false; }
		final Boolean fading = layer.getFading();
		return fading == null ? false : fading;
	}

	public void clear(final WebOpenGL gl) {

		if (objects != null) {
			final int sizeLimit = getTrace();
			isFading = getFading();
			final int size = objects.size();
			for (int i = 0, n = size - sizeLimit; i < n; i++) {
				final List<AbstractObject> list = objects.poll();
			}
			currentList = newCurrentList();
			objects.offer(currentList);
		} else
			currentList.clear();
		final Integer index = openGLListIndex;
		if (index != null) {
			gl.deleteList(index);
			openGLListIndex = null;
		}

		sceneIsInitialized = false;

	}

	public boolean isInvalid() {
		return isInvalid;
	}

	public void invalidate() {
		isInvalid = true;
	}

	public boolean hasTrace() {
		return getTrace() > 0;
	}

	public void setOverlay(final boolean b) {
		overlay = b;
	}

	public boolean isLocked() {
		return locked;
	}

	public void lock() {
		locked = true;
	}

	public void unlock() {
		locked = false;
	}

	public boolean isOverlay() {
		return overlay;
	}

	// public SimpleLayer toSimpleLayer() {
	//
	// final List<DrawingEntity> drawingEntityList = new ArrayList<DrawingEntity>();
	// // we don't send the "constantRedrawnLayer" (like the rotation helper)
	// if (!constantRedrawnLayer) {
	// for (final List<AbstractObject> list : objects) {
	// for (final AbstractObject object : list) {
	// final DrawingEntity[] drawingEntities = renderer.getDrawingEntityGenerator()
	// .generateDrawingEntities(renderer.getSurface().getScope(), object, false, this, null);
	// // explicitly passes null for the OpenGL context
	// if (drawingEntities != null) {
	// for (final DrawingEntity drawingEntity : drawingEntities) {
	// drawingEntityList.add(drawingEntity);
	// }
	// }
	// }
	// }
	// }
	// return new SimpleLayer(getOffset(), getScale(), alpha, drawingEntityList);
	// }

	public int numberOfTraces() {
		return objects == null ? 1 : objects.size();
	}

}
