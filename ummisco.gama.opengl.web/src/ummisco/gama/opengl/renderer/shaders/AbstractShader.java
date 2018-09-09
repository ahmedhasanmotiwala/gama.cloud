/*********************************************************************************************
 *
 * 'AbstractShader.java, in plugin ummisco.gama.opengl, is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl.renderer.shaders;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Scanner;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

import com.jogamp.opengl.GL2;

import ummisco.gama.dev.utils.DEBUG;

public abstract class AbstractShader {

	protected GL2 gl;

	protected boolean isOverlay = false;

	private int programID;
//	private final int vertexShaderID;
//	private final int fragmentShaderID;

	private int location_transformationMatrix;
	private int location_projectionMatrix;
	private int location_viewMatrix;
	private int location_layerAlpha;

	public static final int POSITION_ATTRIBUTE_IDX = 0;
	public static final int COLOR_ATTRIBUTE_IDX = 1;
	public static final int NORMAL_ATTRIBUTE_IDX = 2;
	public static final int UVMAPPING_ATTRIBUTE_IDX = 3;

	private static FloatBuffer matrixBuffer = FloatBuffer.allocate(16);

	protected AbstractShader(final GL2 gl, final String vertexFile, final String fragmentFile) {
		this.gl = gl;
		InputStream vertexInputStream, fragmentInputStream;

//		try {
//			vertexInputStream = getClass().getResourceAsStream(vertexFile);
//			if (vertexInputStream == null) { throw new RuntimeException(
//					"Cannot locate vertex shader program " + vertexFile); }
//			fragmentInputStream = getClass().getResourceAsStream(fragmentFile);
//			if (fragmentInputStream == null) { throw new RuntimeException(
//					"Cannot locate vertex shader program " + vertexFile); }
//		} catch (final Exception e) {
//			DEBUG.ERR(e.getMessage());
//			vertexShaderID = -1;
//			fragmentShaderID = -1;
//			return;
//		}

//		vertexShaderID = loadShader(vertexInputStream, GL2.GL_VERTEX_SHADER);
//		fragmentShaderID = loadShader(fragmentInputStream, GL2.GL_FRAGMENT_SHADER);

		// Each shaderProgram must have
		// one vertex shader and one fragment shader.
		programID = this.gl.glCreateProgram();
//		this.gl.glAttachShader(programID, vertexShaderID);
//		this.gl.glAttachShader(programID, fragmentShaderID);

		// Associate attribute ids with the attribute names inside
		// the vertex shader.
		bindAttributes();

		this.gl.glLinkProgram(programID);
		this.gl.glValidateProgram(programID);

		getAllUniformLocations();
	}

	private int loadShader(final InputStream is, final int type) {
		String shaderString = null;

		try (Scanner s = new java.util.Scanner(is);) {
			s.useDelimiter("\\A");
			shaderString = s.hasNext() ? s.next() : "";

			final int shaderID = gl.glCreateShader(type);

			// Compile the vertexShader String into a program.
			final String[] vlines = new String[] { shaderString };
			final int[] vlengths = new int[] { vlines[0].length() };
			gl.glShaderSource(shaderID, vlines.length, vlines, vlengths, 0);
			gl.glCompileShader(shaderID);
			gl.glEnable(GL2.GL_BLEND);
			gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

			// Check compile status.
			final int[] compiled = new int[1];
			gl.glGetShaderiv(shaderID, GL2.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				final int[] logLength = new int[1];
				gl.glGetShaderiv(shaderID, GL2.GL_INFO_LOG_LENGTH, logLength, 0);

				final byte[] log = new byte[logLength[0]];
				gl.glGetShaderInfoLog(shaderID, logLength[0], (int[]) null, 0, log, 0);

				DEBUG.ERR("Error compiling the vertex shader: " + new String(log));
			}

			return shaderID;
		}
	}

	public void start() {
		gl.glUseProgram(programID);
	}

	public void stop() {
		gl.glUseProgram(0);
	}

	public void cleanUp() {
		stop();
//		gl.glDetachShader(programID, vertexShaderID);
//		gl.glDetachShader(programID, fragmentShaderID);
//		gl.glDeleteShader(vertexShaderID);
//		gl.glDeleteShader(fragmentShaderID);
		gl.glDeleteProgram(programID);
	}

	public int getProgramID() {
		return programID;
	}

	public void setProgramID(final int programID) {
		this.programID = programID;
	}

	public int getUniformLocation(final String uniformName) {
		return gl.glGetUniformLocation(programID, uniformName);
	}

	protected abstract void bindAttributes();

	protected void bindAttribute(final int attribute, final String variableName) {
		gl.glBindAttribLocation(programID, attribute, variableName);
	}

	protected void getAllUniformLocations() {
		location_transformationMatrix = getUniformLocation("transformationMatrix");
		location_projectionMatrix = getUniformLocation("projectionMatrix");
		location_viewMatrix = getUniformLocation("viewMatrix");
		location_layerAlpha = getUniformLocation("layerAlpha");
	}

	static public FloatBuffer getFloatBuffer(final Matrix4f matrix) {
		final FloatBuffer result = FloatBuffer.allocate(16);
		result.put(0, matrix.m00);
		result.put(1, matrix.m01);
		result.put(2, matrix.m02);
		result.put(3, matrix.m03);
		result.put(4, matrix.m10);
		result.put(5, matrix.m11);
		result.put(6, matrix.m12);
		result.put(7, matrix.m13);
		result.put(8, matrix.m20);
		result.put(9, matrix.m21);
		result.put(10, matrix.m22);
		result.put(11, matrix.m23);
		result.put(12, matrix.m30);
		result.put(13, matrix.m31);
		result.put(14, matrix.m32);
		result.put(15, matrix.m33);
		return result;
	}

	protected void loadMatrix(final int location, final Matrix4f matrix) {
		matrixBuffer = getFloatBuffer(matrix);
		matrixBuffer.flip();
		gl.glUniformMatrix4fv(location, 1, false, matrixBuffer.array(), 0);
	}

	public void loadFloat(final int location, final float value) {
		gl.glUniform1f(location, value);
	}

	protected void loadInt(final int location, final int value) {
		gl.glUniform1i(location, value);
	}

	protected void loadVector(final int location, final Vector3f vector) {
		gl.glUniform3f(location, vector.x, vector.y, vector.z);
	}

	public void loadTransformationMatrix(final Matrix4f matrix) {
		if (isOverlay) {
			matrix.setIdentity();
		}
		loadMatrix(location_transformationMatrix, matrix);
	}

	public void loadProjectionMatrix(final Matrix4f matrix) {
		if (isOverlay) {
			matrix.setIdentity();
			matrix.setScale(2f);
			matrix.m30 = -1f;
			matrix.m31 = 1f;
			matrix.m11 = -matrix.m11;
		}
		loadMatrix(location_projectionMatrix, matrix);
	}

	public void loadViewMatrix(final Matrix4f viewMatrix) {
		if (isOverlay) {
			viewMatrix.setIdentity();
		}
		loadMatrix(location_viewMatrix, viewMatrix);
	}

	public void setLayerAlpha(final float layerAlpha) {
		loadFloat(location_layerAlpha, layerAlpha);
	}

	public Vector3f getTranslation() {
		return new Vector3f(0, 0, 0);
	}

	public void enableOverlay(final boolean value) {
		isOverlay = value;
	}

	public boolean isOverlay() {
		return isOverlay;
	}

	abstract public boolean useNormal();

	abstract public boolean useTexture();

	abstract public int getTextureID();
}
