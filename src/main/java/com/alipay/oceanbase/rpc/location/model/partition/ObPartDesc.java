/*-
 * #%L
 * OBKV Table Client Framework
 * %%
 * Copyright (C) 2021 OceanBase
 * %%
 * OBKV Table Client Framework is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 * #L%
 */

package com.alipay.oceanbase.rpc.location.model.partition;

import com.alipay.oceanbase.rpc.exception.ObTablePartitionConsistentException;
import com.alipay.oceanbase.rpc.protocol.payload.impl.ObColumn;
import com.alipay.oceanbase.rpc.util.StringUtil;
import com.alipay.oceanbase.rpc.util.TableClientLoggerFactory;
import org.slf4j.Logger;

import java.util.*;

import static com.alipay.oceanbase.rpc.constant.Constants.EMPTY_STRING;
import static com.alipay.oceanbase.rpc.protocol.payload.impl.column.ObSimpleColumn.DEFAULT_UTF8MB4_GENERAL_CI;
import static com.alipay.oceanbase.rpc.util.TableClientLoggerFactory.LCD;
import static java.util.Collections.*;

public abstract class ObPartDesc {

    private static final Logger                     logger                              = TableClientLoggerFactory
                                                                                            .getLogger(ObPartDesc.class);
    private ObPartFuncType                          partFuncType                        = ObPartFuncType.UNKNOWN;
    private String                                  partExpr                            = EMPTY_STRING;
    @SuppressWarnings("unchecked")
    protected List<String>                          orderedPartColumnNames              = EMPTY_LIST;
    @SuppressWarnings("unchecked")
    protected List<ObPair<ObColumn, List<Integer>>> orderedPartRefColumnRowKeyRelations = EMPTY_LIST;
    @SuppressWarnings("unchecked")
    protected List<ObColumn>                        partColumns                         = EMPTY_LIST;
    private Map<String, Long>                       partNameIdMap                       = null;
    public static final ObPartitionKey              DEFAULT_PART_KEY                    = ObPartitionKey
                                                                                            .getInstance(
                                                                                                Collections
                                                                                                    .singletonList(DEFAULT_UTF8MB4_GENERAL_CI),
                                                                                                "default");
    @SuppressWarnings("unchecked")
    protected Map<String, Integer>                  rowKeyElement                       = EMPTY_MAP;

    /*
     * Get part func type.
     */
    public ObPartFuncType getPartFuncType() {
        return partFuncType;
    }

    /*
     * Set part func type.
     */
    public void setPartFuncType(ObPartFuncType partFuncType) {
        this.partFuncType = partFuncType;
    }

    /*
     * Get part expr.
     */
    public String getPartExpr() {
        return partExpr;
    }

    /*
     * Set part expr.
     */
    public void setPartExpr(String partExpr) {
        if (StringUtil.isBlank(partExpr)) {
            throw new IllegalArgumentException("ObKeyPartDesc part express is blank");
        }
        this.partExpr = partExpr.replace(" ", "");
        this.orderedPartColumnNames = unmodifiableList(Arrays.asList(this.partExpr.split(",")));
    }

    /*
     * Get part num
     */
    public int getPartNum() {
        return -1;
    };

    /*
     * Get ordered part column names.
     */
    public List<String> getOrderedPartColumnNames() {
        return orderedPartColumnNames;
    }

    /*
     * Get part columns.
     */
    public List<ObColumn> getPartColumns() {
        return partColumns;
    }

    /*
     * Set part columns.
     */
    public void setPartColumns(List<ObColumn> partColumns) {
        this.partColumns = partColumns;
    }

    public Map<String, Long> getPartNameIdMap() {
        return this.partNameIdMap;
    }

    /*
     * Set part name id map.
     */
    public void setPartNameIdMap(Map<String, Long> partNameIdMap) {
        this.partNameIdMap = partNameIdMap;
    }

    public Map<String, Integer> getRowKeyElement() {
        return rowKeyElement;
    }

    /*
     * Set row key element.
     */
    public void setRowKeyElement(Map<String, Integer> rowKeyElement) {
        this.rowKeyElement = rowKeyElement;
    }

    protected List<Comparable> initComparableElementByTypes(List<Object> objects,
                                                            List<ObColumn> obColumns) {
        List<Comparable> comparableElement = new ArrayList<Comparable>(objects.size());
        try {
            for (int i = 0; i < objects.size(); i++) {
                ObColumn obColumn = obColumns.get(i);
                comparableElement.add(obColumn.getObObjType().parseToComparable(objects.get(i),
                    obColumn.getObCollationType()));
            }
        } catch (Exception e) {
            logger.error(LCD.convert("01-00024"), objects, obColumns, e);
            throw new IllegalArgumentException(String.format(
                "failed to cast obj, obj=%s, types=%s", objects, obColumns), e);
        }
        return comparableElement;
    }

    //to prepare partition calculate resource
    //to check partition calculate is ready
    public void prepare() throws IllegalArgumentException {
        if (orderedPartColumnNames == EMPTY_LIST) {
            throw new IllegalArgumentException(
                "prepare ObPartDesc failed. orderedPartColumnNames is empty");
        }

        if (rowKeyElement == null || rowKeyElement.size() == 0) {
            throw new IllegalArgumentException("prepare ObPartDesc failed. rowKeyElement is empty");
        }

        if (partColumns == null || partColumns.size() == 0) {
            throw new IllegalArgumentException("prepare ObPartDesc failed. partColumns is empty");
        }
        List<ObPair<ObColumn, List<Integer>>> orderPartRefColumnRowKeyRelations = new ArrayList<ObPair<ObColumn, List<Integer>>>(
            orderedPartColumnNames.size());
        for (String partOrderColumnName : orderedPartColumnNames) {
            for (ObColumn column : partColumns) {
                if (column.getColumnName().equalsIgnoreCase(partOrderColumnName)) {
                    List<Integer> partRefColumnRowKeyIndexes = new ArrayList<Integer>(column
                        .getRefColumnNames().size());
                    for (String refColumn : column.getRefColumnNames()) {
                        boolean rowKeyElementRefer = false;
                        for (String rowKeyElementName : rowKeyElement.keySet()) {
                            if (rowKeyElementName.equalsIgnoreCase(refColumn)) {
                                partRefColumnRowKeyIndexes
                                    .add(rowKeyElement.get(rowKeyElementName));
                                rowKeyElementRefer = true;
                            }
                        }
                        if (!rowKeyElementRefer) {
                            throw new IllegalArgumentException("partition order column "
                                                               + partOrderColumnName
                                                               + " refer to non-row-key column "
                                                               + refColumn);
                        }
                    }
                    orderPartRefColumnRowKeyRelations.add(new ObPair<ObColumn, List<Integer>>(
                        column, partRefColumnRowKeyIndexes));
                }
            }
        }
        this.orderedPartRefColumnRowKeyRelations = orderPartRefColumnRowKeyRelations;
    }

    /*
     * Eval row key values.
     */
    public List<Object> evalRowKeyValues(Object... rowKey) throws IllegalArgumentException {
        int partRefColumnSize = orderedPartRefColumnRowKeyRelations.size();
        List<Object> evalValues = new ArrayList<Object>(partRefColumnSize);
        // column or generate column
        for (int i = 0; i < partRefColumnSize; i++) {
            ObPair<ObColumn, List<Integer>> orderedPartRefColumnRowKeyRelation = orderedPartRefColumnRowKeyRelations
                .get(i);
            Object[] partKey;
            if (rowKey.length < rowKeyElement.size()) {
                throw new IllegalArgumentException("row key is consist of " + rowKeyElement
                                                   + "but found" + Arrays.toString(rowKey));
            } else {
                partKey = Arrays.copyOfRange(rowKey, 0, rowKeyElement.size());
            }
            // row key is consists of multi column
            List<Integer> refIndex = orderedPartRefColumnRowKeyRelation.getRight();
            Object[] evalParams = new Object[refIndex.size()];
            for (int j = 0; j < refIndex.size(); j++) {
                //TODO where get the type of ref column ?
                evalParams[j] = partKey[refIndex.get(j)];
            }
            evalValues.add(orderedPartRefColumnRowKeyRelation.getLeft().evalValue(evalParams));
        }
        return evalValues;
    }

    /*
     *
     * @param start the start row key
     * @param startInclusive the start row key inclusive
     * @param end   the end row key
     * @param endInclusive the end row key inclusive
     */
    public abstract List<Long> getPartIds(Object[] start, boolean startInclusive, Object[] end,
                                          boolean endInclusive) throws IllegalArgumentException;

    public abstract Long getPartId(Object... rowKey) throws IllegalArgumentException;

    public abstract Long getPartId(List<Object[]> rowKeys, boolean consistency)
                                                                               throws IllegalArgumentException,
                                                                               ObTablePartitionConsistentException;

    public abstract Long getRandomPartId();
}
