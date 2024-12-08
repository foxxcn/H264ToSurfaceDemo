package com.appsinthesky.demo.h264tosurfacedemo;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.util.Log;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private TextureView m_surface;
    private MediaCodec m_codec;
    private DecodeFramesTask m_frameTask;
    private static final String SERVER_IP = "192.168.77.124"; // 编码机器IP
    private static final int SERVER_PORT = 21118;
    private static final int FRAME_SIZE = 1024 * 1024; // 假设每帧最大1MB，根据实际情况调整

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取UI中TextureView的引用
        m_surface = findViewById(R.id.textureView);

        // 添加此类作为回调，以捕获Surface Texture的事件
        m_surface.setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_SIZE);

            // 添加一些关键的配置参数
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);

            Surface decodeSurface = new Surface(surface);
            m_codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            m_codec.configure(format, decodeSurface, null, 0);
            m_codec.start();

            m_frameTask = new DecodeFramesTask(m_codec, SERVER_IP, SERVER_PORT);
            m_frameTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing decoder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (m_frameTask != null) {
            m_frameTask.cancel(true);
        }
        if (m_codec != null) {
            m_codec.stop();
            m_codec.release();
            m_codec = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private static class DecodeFramesTask extends AsyncTask<Void, Void, Void> {
        private final MediaCodec codec;
        private final String serverIp;
        private final int serverPort;
        private Socket socket;
        private DataInputStream dataInputStream;

        DecodeFramesTask(MediaCodec codec, String serverIp, int serverPort) {
            this.codec = codec;
            this.serverIp = serverIp;
            this.serverPort = serverPort;
        }

        @Override
        protected Void doInBackground(Void... params) {
            int frameCount = 0;
            try {
                Log.d("DecodeFramesTask", "开始连接服务器: " + serverIp + ":" + serverPort);
                socket = new Socket(serverIp, serverPort);
                dataInputStream = new DataInputStream(socket.getInputStream());
                Log.d("DecodeFramesTask", "服务器连接成功");

                ByteBuffer inputBuffer;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean isEOS = false;
                byte[] lenBuffer = new byte[4];
                frameCount = 0;

                while (!isCancelled()) {
                    try {
                        if (!isEOS) {
                            int inputBufferIndex = codec.dequeueInputBuffer(10000);
                            if (inputBufferIndex >= 0) {
                                // 读取帧长度
                                dataInputStream.readFully(lenBuffer);
                                int frameLength = ByteBuffer.wrap(lenBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                                Log.d("DecodeFramesTask", "收到帧长度: " + frameLength);

                                if (frameLength <= 0 || frameLength > FRAME_SIZE) {
                                    Log.e("DecodeFramesTask", "无效的帧长度: " + frameLength);
                                    continue;
                                }

                                // 读取帧数据
                                inputBuffer = codec.getInputBuffer(inputBufferIndex);
                                if (inputBuffer == null) {
                                    Log.e("DecodeFramesTask", "获取输入缓冲区失败");
                                    continue;
                                }

                                byte[] frameData = new byte[frameLength];
                                dataInputStream.readFully(frameData);
                                Log.d("DecodeFramesTask", "成功读取帧数据，大小: " + frameData.length);

                                inputBuffer.clear();
                                inputBuffer.put(frameData);

                                long presentationTimeUs = System.nanoTime() / 1000;
                                codec.queueInputBuffer(inputBufferIndex, 0, frameLength, presentationTimeUs, 0);
                                Log.d("DecodeFramesTask", "成功将帧数据加入解码队列");
                                frameCount++;
                            }
                        }

                        int outputBufferIndex = codec.dequeueOutputBuffer(info, 10000);
                        Log.d("DecodeFramesTask", "解码输出缓冲区索引: " + outputBufferIndex);

                        if (outputBufferIndex >= 0) {
                            try {
                                codec.releaseOutputBuffer(outputBufferIndex, true);
                                Log.d("DecodeFramesTask", "成功显示第 " + frameCount + " 帧");
                            } catch (MediaCodec.CodecException e) {
                                Log.e("DecodeFramesTask", "释放输出缓冲区失败: " + e.getMessage());
                                Log.e("DecodeFramesTask", "错误代码: " + e.getErrorCode());
                                Log.e("DecodeFramesTask", "诊断信息: " + e.getDiagnosticInfo());
                                e.printStackTrace();
                            }
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = codec.getOutputFormat();
                            Log.d("DecodeFramesTask", "输出格式已更改: " + newFormat.toString());
                        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            Log.d("DecodeFramesTask", "解码器暂时没有输出");
                        }

                    } catch (Exception e) {
                        Log.e("DecodeFramesTask", "处理帧时发生错误: " + e.getMessage());
                        e.printStackTrace();
                        Thread.sleep(100);
                    }
                }
            } catch (Exception e) {
                Log.e("DecodeFramesTask", "致命错误: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Log.d("DecodeFramesTask", "解码任务结束，共处理 " + frameCount + " 帧");
                cleanup();
            }
            return null;
        }

        private void cleanup() {
            try {
                if (dataInputStream != null)
                    dataInputStream.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                Log.e("DecodeFramesTask", "Error closing connection: " + e.getMessage());
            }

            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception e) {
                Log.e("DecodeFramesTask", "Error releasing codec: " + e.getMessage());
            }
        }

        @Override
        protected void onCancelled() {
            // 在任务被取消时也释放资源
            try {
                codec.stop();
                codec.release();
                Log.d("DecodeFramesTask", "Decoder stopped and released on cancellation");
            } catch (Exception e) {
                Log.e("DecodeFramesTask", "Error stopping decoder on cancellation: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (m_frameTask != null) {
            m_frameTask.cancel(true);
        }
    }
}
