package com.amazonaws.glue.catalog.util;

import com.amazonaws.services.glue.AWSGlue;
import com.amazonaws.services.glue.model.BatchDeletePartitionRequest;
import com.amazonaws.services.glue.model.BatchDeletePartitionResult;
import com.amazonaws.services.glue.model.EntityNotFoundException;
import com.amazonaws.services.glue.model.GetPartitionRequest;
import com.amazonaws.services.glue.model.GetPartitionResult;
import com.amazonaws.services.glue.model.InternalServiceException;
import com.amazonaws.services.glue.model.Partition;
import com.amazonaws.services.glue.model.PartitionError;

import com.google.common.collect.Lists;
import org.apache.hadoop.hive.metastore.api.InvalidInputException;
import org.apache.hadoop.hive.metastore.api.InvalidObjectException;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.List;

import static com.amazonaws.glue.catalog.util.TestObjects.getPartitionError;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BatchDeletePartitionsHelperTest {

  @Mock
  private AWSGlue client;

  private BatchDeletePartitionsHelper batchDeletePartitionsHelper;

  private static final String NAMESPACE_NAME = "ns";
  private static final String TABLE_NAME = "table";

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDeletePartitionsEmpty() throws Exception {
    mockBatchDeleteSuccess();

    List<Partition> partitions = Lists.newArrayList();
    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
      .deletePartitions();

    assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().isEmpty());
    assertNull(batchDeletePartitionsHelper.getFirstTException());
  }

  @Test
  public void testDeletePartitionsSucceed() throws Exception {
    mockBatchDeleteSuccess();
    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    List<Partition> partitions = Lists.newArrayList(
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1),
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2));
    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
          .deletePartitions();

    assertEquals(2, batchDeletePartitionsHelper.getPartitionsDeleted().size());
    assertNull(batchDeletePartitionsHelper.getFirstTException());
    for (Partition partition : partitions) {
      assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().contains(partition));
    }
  }

  @Test
  public void testDeletePartitionsThrowsRuntimeException() throws Exception {
    Exception e = new NullPointerException("foo");
    mockBatchDeleteThrowsException(e);

    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    List<Partition> partitions = Lists.newArrayList(
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1),
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2));
    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions);

    batchDeletePartitionsHelper.deletePartitions();
    assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().isEmpty());
    assertNotNull(batchDeletePartitionsHelper.getFirstTException());
    assertEquals("foo", batchDeletePartitionsHelper.getFirstTException().getMessage());
  }

  @Test
  public void testDeletePartitionsThrowsInvalidInputException() throws Exception {
    Exception e = new com.amazonaws.services.glue.model.InvalidInputException("foo");
    mockBatchDeleteThrowsException(e);

    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    List<Partition> partitions = Lists.newArrayList(
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1),
          TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2));
    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions);

    batchDeletePartitionsHelper.deletePartitions();
    assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().isEmpty());
    assertThat(batchDeletePartitionsHelper.getFirstTException(), is(instanceOf(InvalidObjectException.class)));
  }

  @Test
  public void testDeletePartitionsThrowsServiceException() throws Exception {
    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    List<String> values3 = Lists.newArrayList("val3");
    Partition partition1 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1);
    Partition partition2 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2);
    Partition partition3 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values3);
    List<Partition> partitions = Lists.newArrayList(partition1, partition2, partition3);

    Exception e = new InternalServiceException("foo");
    mockBatchDeleteThrowsException(e);
    Mockito.when(client.getPartition(Mockito.any(GetPartitionRequest.class)))
          .thenReturn(new GetPartitionResult().withPartition(partition1))
          .thenThrow(new EntityNotFoundException("bar"))
          .thenThrow(new NullPointerException("baz"));

    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
          .deletePartitions();

    assertThat(batchDeletePartitionsHelper.getFirstTException(), is(instanceOf(MetaException.class)));
    assertThat(batchDeletePartitionsHelper.getPartitionsDeleted(), hasItems(partition2));
    assertThat(batchDeletePartitionsHelper.getPartitionsDeleted(), not(hasItems(partition1, partition3)));
  }

  @Test
  public void testDeletePartitionsDuplicateValues() throws Exception {
    mockBatchDeleteSuccess();

    List<String> values1 = Lists.newArrayList("val1");
    Partition partition = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1);
    List<Partition> partitions = Lists.newArrayList(partition, partition);
    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
          .deletePartitions();

    assertEquals(1, batchDeletePartitionsHelper.getPartitionsDeleted().size());
    assertNull(batchDeletePartitionsHelper.getFirstTException());
    for (Partition p : partitions) {
      assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().contains(p));
    }
  }

  @Test
  public void testDeletePartitionsWithFailure() throws Exception {
    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    Partition partition1 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1);
    Partition partition2 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2);
    List<Partition> partitions = Lists.newArrayList(partition1, partition2);

    PartitionError error = getPartitionError(values1, new EntityNotFoundException("foo error msg"));
    mockBatchDeleteWithFailures(Lists.newArrayList(error));

    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
          .deletePartitions();

    assertEquals(1, batchDeletePartitionsHelper.getPartitionsDeleted().size());
    assertTrue(batchDeletePartitionsHelper.getPartitionsDeleted().contains(partition2));
    assertTrue(batchDeletePartitionsHelper.getFirstTException() instanceof NoSuchObjectException);
  }

  @Test
  public void testDeletePartitionsWithFailures() throws Exception {
    List<String> values1 = Lists.newArrayList("val1");
    List<String> values2 = Lists.newArrayList("val2");
    Partition partition1 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values1);
    Partition partition2 = TestObjects.getTestPartition(NAMESPACE_NAME, TABLE_NAME, values2);
    List<Partition> partitions = Lists.newArrayList(partition1, partition2);

    PartitionError error1 = getPartitionError(values1, new EntityNotFoundException("foo error msg"));
    PartitionError error2 = getPartitionError(values2, new InvalidInputException("foo error msg2"));
    mockBatchDeleteWithFailures(Lists.newArrayList(error1, error2));

    batchDeletePartitionsHelper = new BatchDeletePartitionsHelper(client, NAMESPACE_NAME, TABLE_NAME, null, partitions)
          .deletePartitions();

    assertEquals(0, batchDeletePartitionsHelper.getPartitionsDeleted().size());
    assertTrue(batchDeletePartitionsHelper.getFirstTException() instanceof NoSuchObjectException);
  }

  private void mockBatchDeleteSuccess() {
    Mockito.when(client.batchDeletePartition(Mockito.any(BatchDeletePartitionRequest.class)))
        .thenReturn(new BatchDeletePartitionResult());
  }

  private void mockBatchDeleteWithFailures(Collection<PartitionError> errors) {
    Mockito.when(client.batchDeletePartition(Mockito.any(BatchDeletePartitionRequest.class)))
        .thenReturn(new BatchDeletePartitionResult().withErrors(errors));
  }

  private void mockBatchDeleteThrowsException(Exception e) {
    Mockito.when(client.batchDeletePartition(Mockito.any(BatchDeletePartitionRequest.class))).thenThrow(e);
  }

}