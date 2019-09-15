package com.black.study

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.provider.SyncStateContract
import android.util.Log
import androidx.work.*
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.*
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.io.File.separator
import retrofit2.http.Url
import retrofit2.http.GET
import retrofit2.http.Streaming
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory
import okio.*


class WorkManagerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            //제약 조건
            .setConstraints(constraints)
            //시작 딜레이
            .setInitialDelay(1000, TimeUnit.MILLISECONDS)
            //재시도 대기시간
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            //데이터 전달
            .setInputData(workDataOf("key" to "??"))
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)


    }

    private fun writeResponseBodyToDisk(body: ResponseBody): Boolean {
        try {
            // todo change the file location/name according to your needs
            val futureStudioIconFile = File("${getExternalFilesDir(null)}/Future Studio Icon.png")

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null

            try {
                val fileReader = ByteArray(4096)

                val fileSize = body.contentLength()
                var fileSizeDownloaded: Long = 0

                inputStream = body.byteStream()
                outputStream = FileOutputStream(futureStudioIconFile)

                while (true) {
                    val read = inputStream!!.read(fileReader)

                    if (read == -1) {
                        break
                    }

                    outputStream!!.write(fileReader, 0, read)

                    fileSizeDownloaded += read.toLong()

                    Log.d("a", "file download: $fileSizeDownloaded of $fileSize")
                }

                outputStream!!.flush()

                return true
            } catch (e: IOException) {
                return false
            } finally {
                if (inputStream != null) {
                    inputStream!!.close()
                }

                if (outputStream != null) {
                    outputStream!!.close()
                }
            }
        } catch (e: IOException) {
            return false
        }

    }

    private var totalFileSize: Int = 0

    @Throws(IOException::class)
    private fun downloadFile(body: ResponseBody) {

        var count: Int = 0
        val data = ByteArray(1024 * 4)
        val fileSize = body.contentLength()
        val bis = BufferedInputStream(body.byteStream(), 1024 * 8)
        val outputFile =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "file.zip")
        val output = FileOutputStream(outputFile)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1
        while (true) {
            count = bis.read(data)
            if(count == -1) break

            total += count.toLong()
            totalFileSize = (fileSize / Math.pow(1024.0, 2.0)).toInt()
            val current = Math.round(total / Math.pow(1024.0, 2.0)).toDouble()

            val progress = (total * 100 / fileSize).toInt()

            val currentTime = System.currentTimeMillis() - startTime

            if (currentTime > 1000 * timeCount) {

//                download.setCurrentFileSize(current.toInt())
//                download.setProgress(progress)
//                sendNotification(download)
                timeCount++
            }

            output.write(data, 0, count)
        }
//        onDownloadComplete()
        output.flush()
        output.close()
        bis.close()

    }


    private fun downloadFile(response: Response<ResponseBody>){
        try {
            // you can access headers of response
            val header = response.headers().get("Content-Disposition")
            // this is specific case, it's up to you how you want to save your file
            // if you are not downloading file from direct link, you might be lucky to obtain file name from header
            val fileName = header?.replace("attachment; filename=", "")
            // will create file in global Music directory, can be any other directory, just don't forget to handle permissions
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile(),
                fileName
            )

            val sink = Okio.buffer(Okio.sink(file))
            // you can access body of response
            sink.writeAll(response.body()?.source())
            sink.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

interface DownloadService{
    @Streaming
    @GET
    fun downloadFile(@Url fileUrl: String): Observable<Response<ResponseBody>>
}

class DownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : RxWorker(appContext, workerParams) {
    override fun createWork(): Single<Result> {
        val input = inputData.getString("key")
        val output = workDataOf("result" to "asdf")
        return Single.fromObservable(Observable.just("1234"))
            .map { Result.success() }
    }



}