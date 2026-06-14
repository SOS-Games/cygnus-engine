package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class StarSystemEditor {

    private static final float GRID_SIZE = 20f;
    private static final float ZOOM_SPEED = 0.1f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.5f;
    private static final float HANDLE_PAD = 6f;

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final OrthographicCamera camera = new OrthographicCamera(800f, 600f);
    private final Vector2 tmp = new Vector2();
    private final Vector2 mapCenter = new Vector2();

    private float insetLeft;
    private float insetTop;
    private float insetRight;
    private float insetBottom;

    private StarSystemData systemData;
    private StarSystemBody selectedBody;
    private StarSystemBody hoveredBody;
    private boolean dragging;

    public enum SelectionKind { NONE, PLANET, STATION }

    private SelectionKind selectionKind = SelectionKind.NONE;

    private final InputAdapter input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (systemData == null) {
                return false;
            }

            Vector2 world = screenToWorld(screenX, screenY);
            StarSystemBody hit = pickBody(world.x, world.y);
            if (hit != null) {
                selectBody(hit);
                dragging = true;
                return true;
            }

            clearSelection();
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!dragging || selectedBody == null || systemData == null) {
                return false;
            }

            Vector2 world = screenToWorld(screenX, screenY);
            snap(world);
            selectedBody.x = world.x;
            selectedBody.y = world.y;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            dragging = false;
            updateHover(screenX, screenY);
            return false;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            if (systemData == null) {
                return false;
            }
            updateHover(screenX, screenY);
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (systemData == null) {
                return false;
            }
            camera.zoom = MathUtils.clamp(camera.zoom + amountY * ZOOM_SPEED, MIN_ZOOM, MAX_ZOOM);
            updateCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            return true;
        }
    };

    public StarSystemEditor() {
        camera.setToOrtho(false);
        camera.zoom = 1f;
    }

    public void loadFromDefinition(FileHandle jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            throw new IllegalArgumentException("Star system JSON must exist");
        }
        clearSelection();
        systemData = StarSystemDataIO.loadFromJson(jsonFile);
        if (systemData == null) {
            throw new IllegalStateException("Failed to parse star system JSON: " + jsonFile.path());
        }
        recenterMap();
        Gdx.app.log("StarSystemEditor", "Loaded star system: " + jsonFile.path());
    }

    public void loadData(StarSystemData data) {
        clearSelection();
        systemData = data;
        if (systemData != null) {
            systemData.normalize();
            recenterMap();
        }
    }

    private void recenterMap() {
        if (systemData == null) {
            return;
        }
        mapCenter.set(
            (systemData.mapMinX() + systemData.mapMaxX()) * 0.5f,
            (systemData.mapMinY() + systemData.mapMaxY()) * 0.5f
        );
    }

    public StarSystemData getSystemData() {
        return systemData;
    }

    public StarSystemBody getSelectedBody() {
        return selectedBody;
    }

    public SelectionKind getSelectionKind() {
        return selectionKind;
    }

    public void clearSelection() {
        selectedBody = null;
        selectionKind = SelectionKind.NONE;
        dragging = false;
    }

    public String getSelectionSummary() {
        if (selectedBody == null || selectionKind == SelectionKind.NONE) {
            return "Click a planet or station to select it.";
        }
        return selectedBody.type + " \"" + selectedBody.name + "\" at ("
            + (int) selectedBody.x + ", " + (int) selectedBody.y + ") size "
            + (int) selectedBody.size
            + (selectedBody.type == StarSystemBody.Kind.SPACE_STATION
                ? ", kind " + selectedBody.stationKind
                : "");
    }

    public void addPlanet() {
        if (systemData == null) {
            return;
        }
        StarSystemBody body = new StarSystemBody();
        body.type = StarSystemBody.Kind.PLANET;
        body.name = "Planet " + (nextIndex(StarSystemBody.Kind.PLANET));
        body.x = mapCenter.x;
        body.y = mapCenter.y;
        body.size = 40f;
        systemData.bodies.add(body);
        selectBody(body);
    }

    public void addStation() {
        if (systemData == null) {
            return;
        }
        StarSystemBody body = new StarSystemBody();
        body.type = StarSystemBody.Kind.SPACE_STATION;
        body.stationKind = StationKind.TRADER;
        body.name = "Station " + (nextIndex(StarSystemBody.Kind.SPACE_STATION));
        body.x = mapCenter.x + 80f;
        body.y = mapCenter.y;
        body.size = 50f;
        systemData.bodies.add(body);
        selectBody(body);
    }

    private int nextIndex(StarSystemBody.Kind kind) {
        int n = 1;
        for (StarSystemBody b : systemData.bodies) {
            if (b.type == kind) {
                n++;
            }
        }
        return n;
    }

    public boolean deleteSelected() {
        if (systemData == null || selectedBody == null) {
            return false;
        }
        systemData.bodies.remove(selectedBody);
        clearSelection();
        return true;
    }

    private void selectBody(StarSystemBody body) {
        selectedBody = body;
        selectionKind = body.type == StarSystemBody.Kind.SPACE_STATION
            ? SelectionKind.STATION
            : SelectionKind.PLANET;
    }

    private StarSystemBody pickBody(float wx, float wy) {
        if (systemData == null) {
            return null;
        }
        float pickPad = HANDLE_PAD * zoomFactor();
        for (int i = systemData.bodies.size() - 1; i >= 0; i--) {
            StarSystemBody body = systemData.bodies.get(i);
            if (body.type == StarSystemBody.Kind.PLANET) {
                if (Vector2.dst(wx, wy, body.x, body.y) <= body.size + pickPad) {
                    return body;
                }
            } else {
                float half = body.size;
                if (wx >= body.x - half - pickPad && wx <= body.x + half + pickPad
                    && wy >= body.y - half - pickPad && wy <= body.y + half + pickPad) {
                    return body;
                }
            }
        }
        return null;
    }

    private void updateHover(int screenX, int screenY) {
        if (dragging) {
            return;
        }
        Vector2 world = screenToWorld(screenX, screenY);
        hoveredBody = pickBody(world.x, world.y);
    }

    private Vector2 screenToWorld(int screenX, int screenY) {
        float clientX = Gdx.graphics.getWidth();
        float clientY = Gdx.graphics.getHeight();
        Vector3 pos = new Vector3(screenX, screenY, 0);
        // Camera position already accounts for panel insets; unproject over full window like ShipEditor.
        camera.unproject(pos, 0f, 0f, clientX, clientY);
        tmp.set(pos.x, pos.y);
        return tmp;
    }

    private void snap(Vector2 v) {
        v.x = Math.round(v.x / GRID_SIZE) * GRID_SIZE;
        v.y = Math.round(v.y / GRID_SIZE) * GRID_SIZE;
    }

    private float zoomFactor() {
        return Math.max(1f, camera.zoom);
    }

    public void update(float deltaTime) {}

    public void render() {
        if (systemData == null) {
            return;
        }

        shapeRenderer.setProjectionMatrix(camera.combined);

        drawGrid();
        drawViewportGuide();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (StarSystemBody body : systemData.bodies) {
            boolean selected = body == selectedBody;
            boolean hovered = body == hoveredBody && !dragging;
            if (body.type == StarSystemBody.Kind.PLANET) {
                shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : Color.ORANGE));
                shapeRenderer.circle(body.x, body.y, body.size);
            } else {
                shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : body.stationKind.displayColor));
                float half = body.size;
                shapeRenderer.rect(body.x - half, body.y - half, half * 2f, half * 2f);
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.5f, 0.5f, 0.55f, 0.8f);
        if (selectedBody != null) {
            if (selectedBody.type == StarSystemBody.Kind.PLANET) {
                shapeRenderer.circle(selectedBody.x, selectedBody.y, selectedBody.size + 4f);
            } else {
                float half = selectedBody.size + 4f;
                shapeRenderer.rect(
                    selectedBody.x - half,
                    selectedBody.y - half,
                    half * 2f,
                    half * 2f
                );
            }
        }
        shapeRenderer.end();
    }

    private void drawGrid() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.18f, 0.2f, 0.28f, 1f);

        float minX = systemData.mapMinX();
        float maxX = systemData.mapMaxX();
        float minY = systemData.mapMinY();
        float maxY = systemData.mapMaxY();

        float startX = (float) Math.floor(minX / GRID_SIZE) * GRID_SIZE;
        float startY = (float) Math.floor(minY / GRID_SIZE) * GRID_SIZE;

        for (float x = startX; x <= maxX; x += GRID_SIZE) {
            shapeRenderer.line(x, minY, x, maxY);
        }
        for (float y = startY; y <= maxY; y += GRID_SIZE) {
            shapeRenderer.line(minX, y, maxX, y);
        }
        shapeRenderer.end();
    }

    /** Dashed outline of the nominal play viewport (worldWidth × worldHeight centered on map). */
    private void drawViewportGuide() {
        float vx = 0f;
        float vy = 0f;
        float vw = systemData.worldWidth;
        float vh = systemData.worldHeight;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.35f, 0.75f, 0.45f, 0.85f);
        shapeRenderer.rect(vx, vy, vw, vh);
        shapeRenderer.end();
    }

    public void setViewportInsets(float left, float top, float right, float bottom) {
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
    }

    public void resize(int width, int height) {
        updateCamera(width, height);
    }

    private void updateCamera(int width, int height) {
        if (systemData == null) {
            return;
        }

        float mapW = systemData.mapWidth();
        float mapH = systemData.mapHeight();
        float aspectRatio = (float) width / (float) height;
        float mapAspect = mapW / mapH;

        if (aspectRatio > mapAspect) {
            camera.viewportHeight = mapH;
            camera.viewportWidth = mapH * aspectRatio;
        } else {
            camera.viewportWidth = mapW;
            camera.viewportHeight = mapW / aspectRatio;
        }

        float viewW = width - insetLeft - insetRight;
        float viewH = height - insetTop - insetBottom;
        float visibleCenterX = insetLeft + viewW * 0.5f;
        float visibleCenterY = insetBottom + viewH * 0.5f;

        float worldPerPixelX = camera.viewportWidth * camera.zoom / Math.max(1f, viewW);
        float worldPerPixelY = camera.viewportHeight * camera.zoom / Math.max(1f, viewH);

        camera.position.x = mapCenter.x - (visibleCenterX - width * 0.5f) * worldPerPixelX;
        camera.position.y = mapCenter.y - (visibleCenterY - height * 0.5f) * worldPerPixelY;
        camera.update();
    }

    public InputAdapter getInputProcessor() {
        return input;
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
