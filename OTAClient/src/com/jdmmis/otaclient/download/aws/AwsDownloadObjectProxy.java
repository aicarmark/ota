package com.jdmmis.otaclient.download.aws;

import java.io.InputStream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
//import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.AmazonS3Exception;

import com.jdmmis.otaclient.download.IDownloadObject;
import com.jdmmis.otaclient.download.DownloadStatus;
import com.jdmmis.otaclient.OTAClientConstant;
import com.jdmmis.otaclient.utils.Utils;

public class AwsDownloadObjectProxy  implements IDownloadObject,
		OTAClientConstant {
	private AmazonS3Client mS3Client;
	
	public AwsDownloadObjectProxy() {

	}

	private void ensureAwsS3Client() {
		if (mS3Client == null) {
			AWSCredentials credencial = new BasicAWSCredentials(
				Utils.Access.getAccessKey(), Utils.Access.getSecretKey());
			mS3Client = new AmazonS3Client(credencial);
			/*try {
				mS3Client.setEndpoint(Utils.OTAString.getDdbEndpoint());
			} catch (Exception e) {
				Utils.Log.e("AwsDownloadObjectProxy ensure S3 Client met an error:" + e);
				e.printStackTrace();
			}*/
		}
	}

	public int getObjectSize(DownloadStatus status, String objectName) {
		ensureAwsS3Client();

		try {
			S3Object s3object = mS3Client.getObject(new GetObjectRequest(
				Utils.OTAString.getS3BucketName(), objectName));
			Utils.Log.d("S3 obj type:" + s3object.getObjectMetadata().getContentType());
			Utils.Log.d("S3 obj size:" + s3object.getObjectMetadata().getContentLength());
			return (int)s3object.getObjectMetadata().getContentLength();
		} catch (Exception e) {
			Utils.Log.e("S3 get object size met an error:" + e);
			e.printStackTrace();
			status.setDownloadError(mapException(e));
		}
		
		return 0;
	}

	public InputStream getObjectStream(DownloadStatus status, String objectName, int start, int end) {
		ensureAwsS3Client();

		try {
			GetObjectRequest rangeObjectRequest = new GetObjectRequest(
				Utils.OTAString.getS3BucketName(), objectName);
			rangeObjectRequest.setRange(start, end);
			S3Object objectPortion = mS3Client.getObject(rangeObjectRequest);
			return objectPortion.getObjectContent();
		} catch (Exception e) {
			Utils.Log.e("S3 get object stream met an error:" + e);
			e.printStackTrace();
			status.setDownloadError(mapException(e));
		}
		
		return null;
	}

	public void close() {
		if (mS3Client != null) {
			mS3Client.shutdown();
			mS3Client = null;
		}
	}

	private ErrorNO mapException(Exception e) {
		if (e instanceof AmazonS3Exception) {
			AmazonS3Exception s3e = (AmazonS3Exception)e;
			int statusCode = s3e.getStatusCode();
			// Resource not found in server.
			if (statusCode == 404) {
				return ErrorNO.ERR_OBJECT_NOT_EXISTS;
			} else {
				return ErrorNO.ERR_SERVER_COMMON;
			}
			
		} else if (e instanceof AmazonServiceException) {
			return ErrorNO.ERR_SERVER_COMMON;

		} else {
			return ErrorNO.ERR_ERROR;
		}
	}
}

