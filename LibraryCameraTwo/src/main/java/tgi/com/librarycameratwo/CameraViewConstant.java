package tgi.com.librarycameratwo;

import android.util.Size;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public interface CameraViewConstant {
    int DEFAULT_VIEW_HEIGHT=400;
    int DEFAULT_VIEW_WIDTH=400;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    int MAX_PREVIEW_HEIGHT = 1080;
    String STAGE_READY_TO_TAKE_PICTURE = "STAGE_READY_TO_TAKE_PICTURE";
    String STAGE_IMAGE_HAS_BEEN_TAKEN = "STAGE_IMAGE_HAS_BEEN_TAKEN";
    String STAGE_YOU_SHOULD_START_PRECAPTURING = "STAGE_YOU_SHOULD_START_PRECAPTURING";
    String STAGE_PREVIEWING = "STAGE_PREVIEWING";
    String STAGE_YOU_SHOULD_START_LOCKING_FOCUS = "STAGE_YOU_SHOULD_START_LOCKING_FOCUS";
    String STAGE_LOCKING_FOCUS="STAGE_LOCKING_FOCUS";
    String STAGE_PRECAPTURING_HAS_BEEN_STARTED = "STAGE_PRECAPTURING_HAS_BEEN_STARTED";
    String STAGE_WAITING_FOR_NON_PRECAPTURE_STATE = "STAGE_WAITING_FOR_NON_PRECAPTURE_STATE";
    int MINIMUM_PREVIEW_SIZE = 320;
    Size DESIRED_TENSOR_FLOW_PREVIEW_SIZE = new Size(640, 480);
    int INPUT_SIZE = 244;
    boolean IS_DEBUG_MODE=true;
}
