package vithh.vithOptimized.render.shader;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

public class Shader {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Shader");
    private final int programId;

    public Shader(String vertexSource, String fragmentSource) {
        int vShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vShader);
        GL20.glAttachShader(programId, fShader);

        GL20.glBindAttribLocation(programId, 0, "in_pos");
        GL20.glBindAttribLocation(programId, 1, "in_uv");
        GL20.glBindAttribLocation(programId, 2, "in_color");
        GL20.glBindAttribLocation(programId, 2, "in_worldPos");
        GL20.glBindAttribLocation(programId, 3, "in_flutter");

        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId);
            LOGGER.error("Shader link failed: {}", log);
        }

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);
    }

    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            LOGGER.error("Shader compile error ({}): {}", type, log);
        }
        return shader;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getUniform(String name) {
        return GL20.glGetUniformLocation(programId, name);
    }

    public void setUniform1i(String name, int value) {
        GL20.glUniform1i(getUniform(name), value);
    }

    public void setUniform1f(String name, float value) {
        GL20.glUniform1f(getUniform(name), value);
    }

    public void setUniform2f(String name, float x, float y) {
        GL20.glUniform2f(getUniform(name), x, y);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        GL20.glUniform3f(getUniform(name), x, y, z);
    }

    public void setUniform4f(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(getUniform(name), x, y, z, w);
    }

    public void setUniformColor(String name, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        setUniform4f(name, r, g, b, a);
    }

    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    public void setUniformMat4(String name, Matrix4f mat) {
        int loc = getUniform(name);
        if (loc != -1) {
            MATRIX_BUFFER.rewind();
            mat.get(MATRIX_BUFFER);
            GL20.glUniformMatrix4fv(loc, false, MATRIX_BUFFER);
        }
    }

    public int getProgramId() {
        return programId;
    }
}
