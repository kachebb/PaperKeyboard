package edu.wisc.perperkeyboard;

/**
 * interface to provide callback method for recording buffer
 * @author jj
 *
 */
public interface RecBufListener {
	public void onRecBufFull(short[] data);
	public void register(RecBuffer r);
}
