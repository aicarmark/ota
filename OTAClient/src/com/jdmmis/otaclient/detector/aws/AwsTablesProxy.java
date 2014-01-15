package com.jdmmis.otaclient.detector.aws;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
//import com.amazonaws.services.dynamodbv2.model.ListTablesRequest;
//import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException;
import com.amazonaws.services.dynamodbv2.model.InternalServerErrorException;
import com.amazonaws.services.dynamodbv2.model.ItemCollectionSizeLimitExceededException;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import com.jdmmis.otaclient.detector.ITables;
import com.jdmmis.otaclient.detector.DeviceItem;
import com.jdmmis.otaclient.detector.DetectResult;
import com.jdmmis.otaclient.detector.ReleaseItem;
//import com.jdmmis.otaclient.detector.ReleaseVersion;
import com.jdmmis.otaclient.OTAClientConstant;
import com.jdmmis.otaclient.utils.Utils;

public class AwsTablesProxy implements ITables,
	OTAClientConstant {

	private AmazonDynamoDBClient mDdbClient;
	
	public AwsTablesProxy() {}

	private void ensureAmazonDdbClient(DetectResult result) {
		if (mDdbClient == null) {
			AWSCredentials credencial = new BasicAWSCredentials(
				Utils.Access.getAccessKey(), Utils.Access.getSecretKey());
			mDdbClient = new AmazonDynamoDBClient(credencial);
			try {
				mDdbClient.setEndpoint(Utils.OTAString.getDdbEndpoint());
			} catch (Exception e) {
				Utils.Log.e("AwsTablesProxy ensure Ddb Client met an error:" + e);
				e.printStackTrace();
				this.close();
				result.setDetectError(mapException(e));
			}
		}
	}

	public DeviceItem getDevice(DetectResult result, String imei) {
		ensureAmazonDdbClient(result);
		if (mDdbClient == null) {
			return null;
		}

		try {
			Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
			key.put(DeviceTable.IMEI, 
				new AttributeValue().withS(imei));

			GetItemRequest getItemRequest = new GetItemRequest()
				.withTableName(Utils.OTAString.getDeviceTableName())
				.withKey(key)
				.withAttributesToGet(Arrays.asList(
					DeviceTable.IMEI, 
					DeviceTable.CURRENT_VERSION, 
					DeviceTable.LATEST_VERSION, 
					DeviceTable.UPGRADE_CONTROL));

			GetItemResult getResult = mDdbClient.getItem(getItemRequest);
			Map<String, AttributeValue> map = getResult.getItem();
			if (map != null && map.size()>0) {
				return new DeviceItem(
					map.get(DeviceTable.IMEI).getS(),
					map.get(DeviceTable.UPGRADE_CONTROL).getS(),
					map.get(DeviceTable.LATEST_VERSION).getS(),
					map.get(DeviceTable.CURRENT_VERSION).getS());
			}
		} catch (Exception e) {
			Utils.Log.e("AwsTableProxy get item meets an error:" + e);
			e.printStackTrace();
			result.setDetectError(mapException(e));
		}
		return null;
	}

	public void updateDevice(DetectResult result, String imei, DeviceItem device) {
		ensureAmazonDdbClient(result);
		if (mDdbClient == null) {
			return;
		}

		try {
			Map<String, AttributeValueUpdate> updateItems = 
				new HashMap<String, AttributeValueUpdate>();
			HashMap<String, AttributeValue> key = new HashMap<String, AttributeValue>();
			key.put(ITables.DeviceTable.IMEI, new AttributeValue().withS(imei));

			updateItems.put(ITables.DeviceTable.CURRENT_VERSION,
				new AttributeValueUpdate()
					.withAction(AttributeAction.PUT)
					.withValue(new AttributeValue().withS(device.getDeviceVersion().getVersion())));

			UpdateItemRequest updateItemRequest = new UpdateItemRequest()
				.withTableName(Utils.OTAString.getDeviceTableName())
				.withKey(key)
				.withReturnValues(ReturnValue.UPDATED_NEW)
				.withAttributeUpdates(updateItems);

			/*UpdateItemResult updateResult =*/ mDdbClient.updateItem(updateItemRequest);
		} catch (Exception e) {
			Utils.Log.e("update device to server met an error:" + e);
			e.printStackTrace();
			result.setDetectError(mapException(e));
		}
	}

	public Map<String, ReleaseItem> getPatches(DetectResult result, 
			String main, String patchFrom, String patchTo) {
		ensureAmazonDdbClient(result);
		if (mDdbClient == null) {
			return null;
		}

		try {
			Map<String, ReleaseItem> patches = new HashMap<String, ReleaseItem>();
			Map<String, Condition> keyConditions = new HashMap<String, Condition>();
			keyConditions.put(ReleaseTable.MAIN_VERSION, 
				new Condition()
					.withComparisonOperator(ComparisonOperator.EQ)
					.withAttributeValueList(
						new AttributeValue().withS(main)));
			keyConditions.put(ReleaseTable.PATCH_VERSION, 
				new Condition()
					.withComparisonOperator(ComparisonOperator.BETWEEN)
					.withAttributeValueList(
						new AttributeValue().withS(patchFrom),
						new AttributeValue().withS(patchTo)));

			QueryRequest queryRequest = new QueryRequest()
				.withTableName(Utils.OTAString.getReleaseTableName())
				.withKeyConditions(keyConditions)
				.withAttributesToGet(Arrays.asList(
					ReleaseTable.MAIN_VERSION, 
					ReleaseTable.PATCH_VERSION, 
					ReleaseTable.DEPENDENT_VERSION, 
					ReleaseTable.MD5));
				//.withConsistentRead(true);

			QueryResult queryResult = mDdbClient.query(queryRequest);
			for (Map<String, AttributeValue> item : queryResult.getItems()){
				ReleaseItem ri = new ReleaseItem(
					item.get(ReleaseTable.MAIN_VERSION).getS(),
					item.get(ReleaseTable.PATCH_VERSION).getS(),
					item.get(ReleaseTable.DEPENDENT_VERSION).getS(),
					item.get(ReleaseTable.MD5).getS());
				patches.put(ri.getVersion(), ri);
				Utils.Log.d("AwsTableProxy add one release, key:" + ri.getVersion()
					+ ", value:" + ri);
			}
			return patches;
		} catch (Exception e) {
			Utils.Log.e("AwsTableProxy get patches meets an error:" + e);
			e.printStackTrace();
			result.setDetectError(mapException(e));
		}
		return null;
	}

	public ReleaseItem getRelease(DetectResult result, String main, String patch) {
		ensureAmazonDdbClient(result);
		if (mDdbClient == null) {
			return null;
		}
		
		ReleaseItem release = null;
		try {
			Map<String, Condition> keyConditions = new HashMap<String, Condition>();
			keyConditions.put(ReleaseTable.MAIN_VERSION, 
				new Condition()
					.withComparisonOperator(ComparisonOperator.EQ)
					.withAttributeValueList(
						new AttributeValue().withS(main)));
			keyConditions.put(ReleaseTable.PATCH_VERSION, 
				new Condition()
					.withComparisonOperator(ComparisonOperator.EQ)
					.withAttributeValueList(
						new AttributeValue().withS(patch)));

			QueryRequest queryRequest = new QueryRequest()
				.withTableName(Utils.OTAString.getReleaseTableName())
				.withKeyConditions(keyConditions)
				.withAttributesToGet(Arrays.asList(
					ReleaseTable.MAIN_VERSION, 
					ReleaseTable.PATCH_VERSION, 
					ReleaseTable.DEPENDENT_VERSION, 
					ReleaseTable.MD5));
				//.withConsistentRead(true);

			QueryResult queryResult = mDdbClient.query(queryRequest);
			for (Map<String, AttributeValue> item : queryResult.getItems()){
				release = new ReleaseItem(
					item.get(ReleaseTable.MAIN_VERSION).getS(),
					item.get(ReleaseTable.PATCH_VERSION).getS(),
					item.get(ReleaseTable.DEPENDENT_VERSION).getS(),
					item.get(ReleaseTable.MD5).getS());
				Utils.Log.d("AwsTableProxy get release:" + release);
				return release;
			}
		} catch (Exception e) {
			Utils.Log.e("AwsTableProxy get releases meets an error:" + e);
			e.printStackTrace();
			result.setDetectError(mapException(e));
		}
		return release;
	}

	public void close() {
		if (mDdbClient != null) {
			mDdbClient.shutdown();
			mDdbClient = null;
		}
	}

	private ErrorNO mapException(Exception e) {
		if (e instanceof ResourceNotFoundException) {
			return ErrorNO.ERR_RESOURCE_NOT_FOUND;

		} else if (e instanceof IllegalArgumentException) {
			return ErrorNO.ERR_PARAM_INCORRECT;
			
		} else if (e instanceof ProvisionedThroughputExceededException) {
			return ErrorNO.ERR_SERVER_OVERLOAD;
			
		} else if (e instanceof InternalServerErrorException) {
			return ErrorNO.ERR_SERVER_INTERNAL;

		} else if (e instanceof ItemCollectionSizeLimitExceededException) {
			return ErrorNO.ERR_DATA_TOO_LARGE;
				
		} else if (e instanceof ConditionalCheckFailedException) {
			return ErrorNO.ERR_PARAM_INCORRECT;

		} else if (e instanceof AmazonServiceException) {
			return ErrorNO.ERR_SERVER_COMMON;
		
		} else {
			return ErrorNO.ERR_ERROR;
		}
	}
}

