package srdjancosic.studentprojekt;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Created by Srdjan on 2015-10-20.
 */
public class MapWrapperLayout extends FrameLayout {

    public interface OnDragListener {
        void onDrag(MotionEvent motionEvent);
    }

    private OnDragListener mOnDragListener;

    public MapWrapperLayout(Context context) {
        super(context);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mOnDragListener != null) {
            mOnDragListener.onDrag(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setOnDragListener(OnDragListener mOnDragListener) {
        this.mOnDragListener = mOnDragListener;
    }
}