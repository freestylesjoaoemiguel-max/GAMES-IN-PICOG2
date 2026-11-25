package com.google.mediapipe.tasks.core.logging;
import android.content.Context;
import com.google.mediapipe.proto.MediaPipeLoggingProto;
/** On-device hand test: disable MediaPipe analytics transport. */
public class RemoteLoggingClient implements LoggingClient {
 public RemoteLoggingClient(Context context) {}
 public void logEvent(MediaPipeLoggingProto.MediaPipeLogExtension event) {}
}
