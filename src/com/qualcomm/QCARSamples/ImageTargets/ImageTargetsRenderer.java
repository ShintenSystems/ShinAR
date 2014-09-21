/*==============================================================================
 Copyright (c) 2012-2013 Qualcomm Connected Experiences, Inc.
 All Rights Reserved.
 ==============================================================================*/

package com.qualcomm.QCARSamples.ImageTargets;

import java.util.LinkedList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import raft.jpct.bones.Animated3D;
import raft.jpct.bones.AnimatedGroup;
import raft.jpct.bones.BonesIO;
import raft.jpct.bones.SkeletonPose;
import raft.jpct.bones.SkinClip;
import android.app.Application;
import android.content.res.Resources;
import android.opengl.GLSurfaceView;
import android.util.DisplayMetrics;

import com.qualcomm.QCAR.QCAR;
import com.threed.jpct.Animation;
import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Interact2D;
import com.threed.jpct.Light;
import com.threed.jpct.Logger;
import com.threed.jpct.Mesh;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;


/** The renderer class for the ImageTargets sample. */
public class ImageTargetsRenderer implements GLSurfaceView.Renderer
{
    public boolean mIsActive = false;
    
    /** Reference to main activity **/
    public ImageTargets mActivity;

	private FrameBuffer fb;

	private World world;

	private float[] modelViewMat;

	private Light sun;

	private Object3D cube;

	private Camera cam;

	private float fov;

	private float fovy;
	
	private AnimatedGroup masterNinja;
	private final List<AnimatedGroup> ninjas = new LinkedList<AnimatedGroup>();
	/** ninja placement locations. values are in angles */
	private static final float[] LOCATIONS = new float[] {0, 180, 90, 270, 45, 225, 315, 135};  

    
    
    /** Native function for initializing the renderer. */
    public native void initRendering();
    
    
    /** Native function to update the renderer. */
    public native void updateRendering(int width, int height);
    
    public ImageTargetsRenderer(ImageTargets activity) {
		this.mActivity = activity;
		
		world = new World();
		//world.setAmbientLight(20, 20, 20);

		sun = new Light(world);
		sun.setIntensity(250, 250, 250);
		
		//Get 3D resource file 2014/9/21 Roshan
	      //Added By C.Roshan 2014/9/22 
  		try {
  			Resources res = mActivity.getResources();
			masterNinja = BonesIO.loadGroup(res.openRawResource(R.raw.ninja));
			createMeshKeyFrames();
  		}catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
  		}
  		
 //Added By C.Roshan 2014/9/22      		

		
		// Create a texture out of the icon...:-)
		TextureManager txtMgr = TextureManager.getInstance();
		if (!txtMgr.containsTexture("texture")) {
			Texture texture = new Texture(BitmapHelper.rescale(
					BitmapHelper.convert(mActivity.getResources().getDrawable(R.drawable.vuforia_splash)), 64, 64));
			txtMgr.addTexture("texture", texture);
		}

		
		world.setAmbientLight(127, 127, 127);
		world.buildAllObjects();

		float[] bb = this.calcBoundingBox();
		float height = (bb[3] - bb[2]); // ninja height
		new Light(world).setPosition(new SimpleVector(0, -height/2, height));
	/*	
		cube = Primitives.getCylinder(20, 40);
		cube.calcTextureWrapSpherical();
		cube.setTexture("texture");
		cube.strip();
		cube.build();

		world.addObject(cube);

		cam = world.getCamera();

		SimpleVector sv = new SimpleVector();
		sv.set(cube.getTransformedCenter());
		sv.y += 100;
		sv.z += 100;
		
		sun.setPosition(sv); */
		
		MemoryHelper.compact();
	}
    
    private void createMeshKeyFrames() {
		Config.maxAnimationSubSequences = masterNinja.getSkinClipSequence().getSize() + 1; // +1 for whole sequence
		
		int keyframeCount = 0;
		final float deltaTime = 0.2f; // max time between frames
		
		for (SkinClip clip : masterNinja.getSkinClipSequence()) {
			float clipTime = clip.getTime();
			int frames = (int) Math.ceil(clipTime / deltaTime) + 1;
			keyframeCount += frames;
		}
		
		Animation[] animations = new Animation[masterNinja.getSize()];
		for (int i = 0; i < masterNinja.getSize(); i++) {
			animations[i] = new Animation(keyframeCount);
			animations[i].setClampingMode(Animation.USE_CLAMPING);
		}
		//System.out.println("------------ keyframeCount: " + keyframeCount + ", mesh size: " + masterNinja.getSize());
		int count = 0;
		
		int sequence = 0;
		for (SkinClip clip : masterNinja.getSkinClipSequence()) {
			float clipTime = clip.getTime();
			int frames = (int) Math.ceil(clipTime / deltaTime) + 1;
			float dIndex = 1f / (frames - 1);
			
			for (int i = 0; i < masterNinja.getSize(); i++) {
				animations[i].createSubSequence(clip.getName());
			}
			//System.out.println(sequence + ": " + clip.getName() + ", frames: " + frames);
			for (int i = 0; i < frames; i++) {
				masterNinja.animateSkin(dIndex * i, sequence + 1);
				
				for (int j = 0; j < masterNinja.getSize(); j++) {
					Mesh keyframe = masterNinja.get(j).getMesh().cloneMesh(true);
					keyframe.strip();
					animations[j].addKeyFrame(keyframe);
					count++;
					//System.out.println("added " + (i + 1) + " of " + sequence + " to " + j + " total: " + count);
				}
			}
			sequence++;
		}
		for (int i = 0; i < masterNinja.getSize(); i++) {
			masterNinja.get(i).setAnimationSequence(animations[i]);
		}
		masterNinja.get(0).getSkeletonPose().setToBindPose();
		masterNinja.get(0).getSkeletonPose().updateTransforms();
		masterNinja.applySkeletonPose();
		masterNinja.applyAnimation();
		
		Logger.log("created mesh keyframes, " + keyframeCount + "x" + masterNinja.getSize());
	}
   
	private void addNinja() {
		if (ninjas.size() == LOCATIONS.length)
			return;
		
		AnimatedGroup ninja = masterNinja.clone(AnimatedGroup.MESH_DONT_REUSE);
		float[] bb = this.calcBoundingBox();
		float radius = (bb[3] - bb[2]) * 0.5f; // half of height
		double angle = Math.toRadians(LOCATIONS[ninjas.size()]);

		ninja.setSkeletonPose(new SkeletonPose(ninja.get(0).getSkeleton()));
		ninja.getRoot().translate((float)(Math.cos(angle) * radius), 0, (float)(Math.sin(angle) * radius));
		
		ninja.addToWorld(world);
		ninjas.add(ninja);
		Logger.log("added new ninja: " + ninjas.size());
	}

	/** calculates and returns whole bounding box of skinned group */
	protected float[] calcBoundingBox() {
		float[] box = null;
		
		for (Animated3D skin : masterNinja) {
			float[] skinBB = skin.getMesh().getBoundingBox();
			
			if (box == null) {
				box = skinBB;
			} else {
				// x
				box[0] = Math.min(box[0], skinBB[0]);
				box[1] = Math.max(box[1], skinBB[1]);
				// y
				box[2] = Math.min(box[2], skinBB[2]);
				box[3] = Math.max(box[3], skinBB[3]);
				// z
				box[4] = Math.min(box[4], skinBB[4]);
				box[5] = Math.max(box[5], skinBB[5]);
			}
		}
		return box;
	}
	
    /** 
     * calculates a camera distance to make object look height pixels on screen 
     * @author EgonOlsen 
     * */
    protected float calcDistance(Camera c, FrameBuffer buffer, float height, float objectHeight) {
        float h = height / 2f;
        float os = objectHeight / 2f;

        Camera cam = new Camera();
        cam.setFOV(c.getFOV());
        SimpleVector p1 = Interact2D.project3D2D(cam, buffer, new SimpleVector(0f, os, 1f));
        float y1 = p1.y - buffer.getCenterY();
        float z = (1f/h) * y1;

        return z;
    }

    
    /** Called when the surface is created or recreated. */
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        DebugLog.LOGD("GLRenderer::onSurfaceCreated");
        
        // Call native function to initialize rendering:
        initRendering();
        
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        QCAR.onSurfaceCreated();
    }
    
    
    /** Called when the surface changed size. */
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
    	DebugLog.LOGD(String.format("GLRenderer::onSurfaceChanged (%d, %d)", width, height));

		if (fb != null) {
			fb.dispose();
		}
		fb = new FrameBuffer(width, height);
		Config.viewportOffsetAffectsRenderTarget=true;
        
        // Call native function to update rendering when render surface
        // parameters have changed:
        updateRendering(width, height);
        
        // Call Vuforia function to handle render surface size changes:
        QCAR.onSurfaceChanged(width, height);
    }
    
    
    /** The native render function. */
    public native void renderFrame();
    
    
    /** Called to draw the current frame. */
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;
        
        // Update render view (projection matrix and viewport) if needed:
        mActivity.updateRenderView();
        
        // Call our native function to render content
        renderFrame();
        
        updateCamera();

		world.renderScene(fb);
		world.draw(fb);
		fb.display();
    }
    
    public void updateModelviewMatrix(float mat[]) {
		modelViewMat = mat;
	}
    
    public void updateCamera() {
		if (modelViewMat != null) {
			float[] m = modelViewMat;

			final SimpleVector camUp;
			if (mActivity.isPortrait()) {
				camUp = new SimpleVector(-m[0], -m[1], -m[2]);
			} else {
				camUp = new SimpleVector(-m[4], -m[5], -m[6]);
			}
			
			final SimpleVector camDirection = new SimpleVector(m[8], m[9], m[10]);
			final SimpleVector camPosition = new SimpleVector(m[12], m[13], m[14]);
			
			cam.setOrientation(camDirection, camUp);
			cam.setPosition(camPosition);
			
			cam.setFOV(fov);
			cam.setYFOV(fovy);
		}
	}

	public void setVideoSize(int videoWidth, int videoHeight) {
		
		DisplayMetrics displaymetrics = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		int height = displaymetrics.heightPixels;
		int width = displaymetrics.widthPixels;
		
		int widestVideo = videoWidth > videoHeight? videoWidth: videoHeight;
		int widestScreen = width > height? width: height;
		
		float diff = (widestVideo - widestScreen) / 2;
		
		Config.viewportOffsetY = diff / widestScreen;
	}
	
	public void setFov(float fov) {
		this.fov = fov;
	}
	
	public void setFovy(float fovy) {
		this.fovy = fovy;
	}
}
