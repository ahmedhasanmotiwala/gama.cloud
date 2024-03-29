/*********************************************************************************************
 *
 * 'Abstract3DRenderer.java, in plugin ummisco.gama.opengl, is part of the source code of the GAMA modeling and
 * simulation platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.List;

import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.FPSCounter;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.swt.GLCanvas;
import com.vividsolutions.jts.geom.Geometry;

import cict.gama.webgl.WebGLComposite;
import msi.gama.common.geometry.Envelope3D;
import msi.gama.common.interfaces.IDisplaySurface;
import msi.gama.common.interfaces.IKeyword;
import msi.gama.common.interfaces.ILayer;
import msi.gama.common.preferences.GamaPreferences;
import msi.gama.metamodel.shape.GamaPoint;
import msi.gama.metamodel.shape.ILocation;
import msi.gama.metamodel.shape.IShape;
import msi.gama.outputs.display.AbstractDisplayGraphics;
import msi.gama.outputs.layers.OverlayLayer;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaColor;
import msi.gama.util.file.GamaImageFile;
import msi.gaml.expressions.IExpression;
import msi.gaml.expressions.PixelUnitExpression;
import msi.gaml.operators.Cast;
import msi.gaml.statements.draw.DrawingAttributes;
import msi.gaml.statements.draw.FileDrawingAttributes;
import msi.gaml.statements.draw.ShapeDrawingAttributes;
import msi.gaml.statements.draw.TextDrawingAttributes;
import msi.gaml.types.GamaGeometryType;
import ummisco.gama.opengl.camera.CameraArcBall;
import ummisco.gama.opengl.camera.FreeFlyCamera;
import ummisco.gama.opengl.camera.ICamera;
import ummisco.gama.opengl.scene.AbstractObject;
import ummisco.gama.opengl.scene.ModelScene;
import ummisco.gama.opengl.scene.ObjectDrawer;
import ummisco.gama.opengl.scene.OpenGL;
import ummisco.gama.opengl.scene.SceneBuffer;
import ummisco.gama.opengl.utils.LightHelper;
import ummisco.gama.opengl.vaoGenerator.DrawingEntityGenerator;
import ummisco.gama.opengl.vaoGenerator.ShapeCache;
import ummisco.gama.ui.utils.WorkbenchHelper;

/**
 * This class plays the role of Renderer and IGraphics. Class Abstract3DRenderer.
 *
 * @author drogoul
 * @since 27 avr. 2015
 *
 */
public abstract class Abstract3DRenderer extends AbstractDisplayGraphics implements GLEventListener {

	public class PickingState {

		final static int NONE = -2;
		final static int WORLD = -1;

		volatile boolean isPicking;
		volatile boolean isMenuOn;
		volatile int pickedIndex = NONE;

		public void setPicking(final boolean isPicking) {
			this.isPicking = isPicking;
			if (!isPicking) {
				setPickedIndex(NONE);
				setMenuOn(false);
			}
		}

		public void setMenuOn(final boolean isMenuOn) {
			this.isMenuOn = isMenuOn;
		}

		public void setPickedIndex(final int pickedIndex) {
			this.pickedIndex = pickedIndex;
			if (pickedIndex == WORLD && !isMenuOn) {
				// Selection occured, but no object have been selected
				setMenuOn(true);
				getSurface().selectAgent(null);
			}
		}

		public void tryPick(final DrawingAttributes attributes) {
			attributes.markSelected(pickedIndex);
			if (attributes.isSelected() && !isMenuOn) {
				setMenuOn(true);
				getSurface().selectAgent(attributes);
			}
		}

		public boolean isBeginningPicking() {
			return isPicking && pickedIndex == NONE;
		}

		public boolean isMenuOn() {
			return isMenuOn;
		}

		public boolean isPicking() {
			return isPicking;
		}

	}

	public final static boolean DRAW_NORM = false;
	protected DrawingEntityGenerator drawingEntityGenerator;
	protected final PickingState pickingState = new PickingState();
	public SceneBuffer sceneBuffer;
	protected ModelScene currentScene;
	protected WebGLComposite canvas;
	public ICamera camera;
	protected LightHelper lightHelper;
	protected volatile boolean inited;
	protected volatile boolean visible;
	// protected volatile boolean shouldRecomputeLayerBounds;

	public boolean colorPicking = false;
	protected GL2 gl;
	protected OpenGL openGL;
	protected GamaPoint worldDimensions;
	protected Envelope3D ROIEnvelope = null;
	// relative to rotation helper
	protected boolean drawRotationHelper = false;
	protected GamaPoint rotationHelperPosition = null;

	// CACHES FOR TEXTURES, FONTS AND GEOMETRIES

	public static Boolean isNonPowerOf2TexturesAvailable = false;
	protected final IntBuffer selectBuffer = Buffers.newDirectIntBuffer(1024);

	@Override
	public void setDisplaySurface(final IDisplaySurface d) {
		super.setDisplaySurface(d);
		d.getScope().setGraphics(this);
		worldDimensions = new GamaPoint(data.getEnvWidth(), data.getEnvHeight());
		camera = new CameraArcBall(this);
		camera.initialize();
		sceneBuffer = new SceneBuffer(this);
		openGL = new OpenGL(this);
		ShapeCache.freedShapeCache();
	}

	public void setUpKeystoneCoordinates() {
		getKeystone().setUpCoords();
	}

	public abstract IKeystoneState getKeystone();

	public GLAutoDrawable createDrawable(final Composite parent) {
		final GLProfile profile = GLProfile.getDefault();
		final GLCapabilities cap = new GLCapabilities(profile);
		cap.setDepthBits(24);
		// cap.setBackgroundOpaque(true);
		cap.setDoubleBuffered(true);
		cap.setHardwareAccelerated(true);
		cap.setSampleBuffers(true);
		cap.setAlphaBits(8);
		cap.setNumSamples(8);

		canvas = new WebGLComposite(parent, SWT.NONE);
		canvas.setAutoSwapBufferMode(true);
//		final SWTGLAnimator animator = new SWTGLAnimator(canvas);
		// animator.setIgnoreExceptions(!GamaPreferences.Runtime.ERRORS_IN_DISPLAYS.getValue());
//		animator.setUpdateFPSFrames(FPSCounter.DEFAULT_FRAMES_PER_INTERVAL, null);
		canvas.addGLEventListener(this);
		final FillLayout gl = new FillLayout();
		canvas.setLayout(gl);
		return canvas;
	}

	public final ModelScene getCurrentScene() {
		return currentScene;
	}

	public LightHelper getLightHelper() {
		return lightHelper;
	}

	public final GLCanvas getCanvas() {
		return canvas;
	}

	public final GLAutoDrawable getDrawable() {
		return canvas;
	}

	protected void initializeCanvasListeners() {

		WorkbenchHelper.asyncRun(GAMA.getRuntimeScope(),() -> {
			if (getCanvas() == null || getCanvas().isDisposed()) { return; }
			getCanvas().addKeyListener(camera);
			getCanvas().addMouseListener(camera);
			getCanvas().addMouseMoveListener(camera);
			//getCanvas().addMouseWheelListener(camera);
			//getCanvas().addMouseTrackListener(camera);

		});

	}

	public final double getMaxEnvDim() {
		// built dynamically to prepare for the changes in size of the
		// environment
		final double env_width = worldDimensions.x;
		final double env_height = worldDimensions.y;
		return env_width > env_height ? env_width : env_height;
	}

	public final double getEnvWidth() {
		return worldDimensions.x;
	}

	public final double getEnvHeight() {
		return worldDimensions.y;
	}

	public DrawingEntityGenerator getDrawingEntityGenerator() {
		return drawingEntityGenerator;
	}

	public final void switchCamera() {
		final ICamera oldCamera = camera;
		WorkbenchHelper.asyncRun(GAMA.getRuntimeScope(),() -> {
			getCanvas().removeKeyListener(oldCamera);
			getCanvas().removeMouseListener(oldCamera);
			getCanvas().removeMouseMoveListener(oldCamera);
			//getCanvas().removeMouseWheelListener(oldCamera);
			//getCanvas().removeMouseTrackListener(oldCamera);
		});

		if (!data.isArcBallCamera()) {
			camera = new FreeFlyCamera(this);
		} else {
			camera = new CameraArcBall(this);
		}
		camera.initialize();
		initializeCanvasListeners();

	}

	@Override
	public double getxRatioBetweenPixelsAndModelUnits() {
		if (currentLayer == null) {
			return getDisplayWidth() / data.getEnvWidth();
		} else if (currentLayer instanceof OverlayLayer) { return this.getViewWidth() / data.getEnvWidth(); }
		return currentLayer.getData().getSizeInPixels().x / data.getEnvWidth();
	}

	@Override
	public double getyRatioBetweenPixelsAndModelUnits() {
		if (currentLayer == null) {
			return getDisplayHeight() / data.getEnvHeight();
		} else if (currentLayer instanceof OverlayLayer) {
			// return getxRatioBetweenPixelsAndModelUnits();
			return this.getViewHeight() / data.getEnvHeight();
		} else {
			return currentLayer.getData().getSizeInPixels().y / data.getEnvHeight();
		}
	}

	public final double getWidth() {
		return getDrawable().getSurfaceWidth() * surface.getZoomLevel();
	}

	public final double getHeight() {
		return getDrawable().getSurfaceHeight() * surface.getZoomLevel();
	}

	public final void updateCameraPosition() {
		camera.update();
	}

	protected abstract void updatePerspective();

	@Override
	public void fillBackground(final Color bgColor, final double opacity) {
		openGL.setCurrentObjectAlpha(opacity);
	}

	/**
	 * Method getDisplayWidthInPixels()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getDisplayWidthInPixels()
	 */
	@Override
	public final int getDisplayWidth() {
		return (int) Math.round(getWidth());
	}

	/**
	 * Method getDisplayHeightInPixels()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getDisplayHeightInPixels()
	 */
	@Override
	public final int getDisplayHeight() {
		return (int) Math.round(getHeight());
	}

	public final GamaPoint getIntWorldPointFromWindowPoint(final Point windowPoint) {
		final GamaPoint p = getRealWorldPointFromWindowPoint(windowPoint);
		return new GamaPoint((int) p.x, (int) p.y);
	}

	public abstract GamaPoint getRealWorldPointFromWindowPoint(final Point windowPoint);

	/**
	 * Method getZoomLevel()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#getZoomLevel()
	 */
	@Override
	public final Double getZoomLevel() {
		return data.getZoomLevel();
	}

	/**
	 * Useful for drawing fonts
	 * 
	 * @return
	 */
	public final double getGlobalYRatioBetweenPixelsAndModelUnits() {
		return getHeight() / data.getEnvHeight();
	}

	/**
	 * Method is2D()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#is2D()
	 */
	@Override
	public final boolean is2D() {
		return false;
	}

	/**
	 * @return
	 */
	public static float getLineWidth() {
		return GamaPreferences.Displays.CORE_LINE_WIDTH.getValue().floatValue();
	}

	@Override
	public final SWTOpenGLDisplaySurface getSurface() {
		return (SWTOpenGLDisplaySurface) surface;
	}
 
	public final ILocation getCameraPos() {
		return camera.getPosition();
	}
 
	public final ILocation getCameraTarget() {
		return camera.getTarget();
	}
 
	public final ILocation getCameraOrientation() {
		return camera.getOrientation();
	}

	public boolean useShader() {
		return false;
	}

	public boolean preloadTextures() {
		return true;
	}

	public abstract boolean mouseInROI(final Point mousePosition);

	// END HELPERS

	@SuppressWarnings ("rawtypes")
	public ObjectDrawer getDrawerFor(final AbstractObject.DrawerType type) {
		return null;
	}

	public double getCurrentZRotation() {
		return data.getCurrentRotationAboutZ();
	}

	public GamaPoint getRotationHelperPosition() {
		return rotationHelperPosition;
	}

	public GamaPoint getWorldsDimensions() {
		return worldDimensions;
	}

	/**
	 * Method drawShape. Add a given JTS Geometry in the list of all the existing geometry that will be displayed by
	 * openGl.
	 */
	@Override
	public Rectangle2D drawShape(final Geometry shape, final ShapeDrawingAttributes attributes) {
		if (shape == null) { return null; }
		if (sceneBuffer.getSceneToUpdate() == null) { return null; }
		tryToHighlight(attributes);
		sceneBuffer.getSceneToUpdate().addGeometry(shape, attributes);
		return rect;
	}

	/**
	 * Method drawImage.
	 *
	 * @param img
	 *            Image
	 * @param angle
	 *            Integer
	 */
	@Override
	public Rectangle2D drawImage(final BufferedImage img, final FileDrawingAttributes attributes) {
		if (sceneBuffer.getSceneToUpdate() == null) { return null; }
		sceneBuffer.getSceneToUpdate().addImage(img, attributes);
		tryToHighlight(attributes);
		if (attributes.getBorder() != null) {
			drawGridLine(new GamaPoint(img.getWidth(), img.getHeight()), attributes.getBorder());
		}
		return rect;
	}

	protected void tryToHighlight(final FileDrawingAttributes attributes) {
		if (highlight) {
			attributes.setHighlighted(data.getHighlightColor());
		}
	}

	public void drawGridLine(final GamaPoint dimensions, final Color lineColor) {
		if (sceneBuffer.getSceneToUpdate() == null) { return; }
		double stepX, stepY;
		final double cellWidth = worldDimensions.x / dimensions.x;
		final double cellHeight = worldDimensions.y / dimensions.y;
		final GamaColor color = GamaColor.getInt(lineColor.getRGB());
		final ShapeDrawingAttributes attributes = new ShapeDrawingAttributes(null, color, color, IShape.Type.GRIDLINE);
		for (double i = 0; i < dimensions.x; i++) {
			for (double j = 0; j < dimensions.y; j++) {
				stepX = i + 0.5;
				stepY = j + 0.5;
				final Geometry g = GamaGeometryType
						.buildRectangle(cellWidth, cellHeight, new GamaPoint(stepX * cellWidth, stepY * cellHeight))
						.getInnerGeometry();
				sceneBuffer.getSceneToUpdate().addGeometry(g, attributes);
			}
		}
	}

	@Override
	public Rectangle2D drawString(final String string, final TextDrawingAttributes attributes) {
		// Multiline: Issue #780
		if (string.contains("\n")) {
			for (final String s : string.split("\n")) {
				attributes.getLocation().setY(attributes.getLocation().getY()
						+ attributes.font.getSize() * this.getyRatioBetweenPixelsAndModelUnits());
				drawString(s, attributes);
			}
			return null;
		}
		attributes.getLocation().setY(-attributes.getLocation().getY());
		sceneBuffer.getSceneToUpdate().addString(string, attributes);
		return null;
	}

	protected void preloadTextures(final DrawingAttributes attributes) {
		if (!preloadTextures())
			return;
		final List<?> textures = attributes.getTextures();
		if (textures != null && !textures.isEmpty()) {
			for (final Object img : textures) {
				if (img instanceof GamaImageFile) {
					openGL.cacheTexture(((GamaImageFile) img).getFile(getSurface().getScope()));
				}
			}
		}
	}

	public void startDrawRotationHelper(final GamaPoint pos) {
		rotationHelperPosition = pos;
		drawRotationHelper = true;
		final double distance = Math.sqrt(Math.pow(camera.getPosition().x - rotationHelperPosition.x, 2)
				+ Math.pow(camera.getPosition().y - rotationHelperPosition.y, 2)
				+ Math.pow(camera.getPosition().z - rotationHelperPosition.z, 2));
		final double size = distance / 10; // the size of the displayed axis
		if (currentScene != null)
			currentScene.startDrawRotationHelper(pos, size);
	}

	public void stopDrawRotationHelper() {
		rotationHelperPosition = null;
		drawRotationHelper = false;
		if (currentScene != null)
			currentScene.stopDrawRotationHelper();
	}

	public void defineROI(final Point start, final Point end) {
		final GamaPoint startInWorld = getRealWorldPointFromWindowPoint(start);
		final GamaPoint endInWorld = getRealWorldPointFromWindowPoint(end);
		ROIEnvelope = new Envelope3D(startInWorld.x, endInWorld.x, startInWorld.y, endInWorld.y, 0, 1);
	}

	public void cancelROI() {
		if (camera.isROISticky())
			return;
		ROIEnvelope = null;
	}

	public PickingState getPickingState() {
		return pickingState;
	}

	public Envelope3D getROIEnvelope() {
		return ROIEnvelope;
	}

	public OpenGL getOpenGLHelper() {
		return openGL;
	}

	public void drawScene() {
		currentScene = sceneBuffer.getSceneToRender();
		if (currentScene == null) { return; }
		// Do some garbage collecting in model scenes
		sceneBuffer.garbageCollect(openGL);
		// if (this.shouldRecomputeLayerBounds) {
		// surface.getManager().recomputeBounds(this);
		// shouldRecomputeLayerBounds = false;
		// }
		// if picking, we draw a first pass to pick the color
		if (pickingState.isBeginningPicking()) {
			beginPicking();
			currentScene.draw(openGL);
			endPicking();
		}
		// we draw the scene on screen
		currentScene.draw(openGL);
	}

	// This method is normally called either when the graphics is created or
	// when the output is changed
	// @Override
	public void initScene() {
		if (sceneBuffer != null) {
			final ModelScene scene = sceneBuffer.getSceneToRender();
			if (scene != null) {
				scene.reload();
			}
		}
	}

	@Override
	public boolean beginDrawingLayers() {
		while (!inited) {
			init(canvas);
			try {
				Thread.sleep(10);
			} catch (final InterruptedException e) {
				return false;
			}
		}
		return sceneBuffer.beginUpdatingScene();

	}

	@Override
	public void dispose() {
		super.dispose();
		dispose(getDrawable());
	}

	@Override
	public void beginDrawingLayer(final ILayer layer) {
		layer.reloadOn(surface);
		super.beginDrawingLayer(layer);
		GamaPoint currentOffset, currentScale;
		currentOffset = new GamaPoint(0, 0);
		final IScope scope = getSurface().getScope();
		final IExpression expr = layer.getDefinition().getFacet(IKeyword.POSITION);

		if (expr != null) {
			final boolean containsPixels = expr.findAny((e) -> e instanceof PixelUnitExpression);
			currentOffset = (GamaPoint) Cast.asPoint(scope, expr.value(scope));
			if (Math.abs(currentOffset.x) <= 1 && !containsPixels) {
				currentOffset.x *= getEnvWidth();
			}
			if (currentOffset.x < 0) {
				currentOffset.x = getEnvWidth() - currentOffset.x;
			}
			if (Math.abs(currentOffset.y) <= 1 && !containsPixels) {
				currentOffset.y *= getEnvHeight();
			}
			if (currentOffset.y < 0) {
				currentOffset.y = getEnvHeight() - currentOffset.y;
			}

		}
		// if (!layer.isOverlay()) {
		// final double currentZLayer = getMaxEnvDim() * layer.getPosition().getZ();
		// double zScale = layer.getExtent().getZ();
		// if (zScale <= 0) {
		// zScale = 1;
		// }
		// currentOffset = new GamaPoint(currentOffset.x, currentOffset.y,
		// currentZLayer);
		// currentScale = new GamaPoint(getLayerWidth() / getWidth(), getLayerHeight() /
		// getHeight(), zScale);
		// } else {
		currentScale = new GamaPoint(0.9, 0.9, 1);
		// } 
		final ModelScene scene = sceneBuffer.getSceneToUpdate();
		if (scene != null) {
			scene.beginDrawingLayer(layer, currentOffset, currentScale, currentLayerAlpha);
		}
	}

	@Override
	public void endDrawingLayer(ILayer layer) {
		super.endDrawingLayer(layer);
		if(sceneBuffer.isNotReadyToUpdate()) {
			display(canvas);
		}
	}
	


	@Override
	public boolean isNotReadyToUpdate() {
		if (data.isSynchronized())
			return false;
//		if(sceneBuffer.isNotReadyToUpdate()) {
//			display(canvas);
//		}
//		return sceneBuffer.isNotReadyToUpdate();
		return false;
	}
	/**
	 * Method endDrawingLayers()
	 * 
	 * @see msi.gama.common.interfaces.IGraphics#endDrawingLayers()
	 */
	@Override
	public void endDrawingLayers() {
		sceneBuffer.endUpdatingScene();
		getSurface().invalidateVisibleRegions();
	}

	// Picking method
	// //////////////////////////////////////////////////////////////////////////////////////
	public abstract void beginPicking();

	public abstract void endPicking();
 
	public int getWidthForOverlay() {
		return getViewWidth();
	}
 
	public int getHeightForOverlay() {
		return getViewHeight();
	}
}
