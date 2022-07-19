/*
 * Copyright 2019 PingCAP, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pingcap.tikv;

import com.pingcap.tikv.util.ConverterUpstream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.tikv.common.region.TiRegion;
import org.tikv.common.region.TiStore;
import org.tikv.kvproto.Metapb;
import org.tikv.kvproto.Pdpb;
import org.tikv.shade.com.google.common.collect.ImmutableList;
import org.tikv.shade.com.google.protobuf.ByteString;

public class MockServerTest extends PDMockServerTest {
  public KVMockServer server;
  public int port;
  public TiRegion region;

  @Before
  @Override
  public void setUp() throws IOException {
    super.setUp();

    Metapb.Region r =
        Metapb.Region.newBuilder()
            .setRegionEpoch(Metapb.RegionEpoch.newBuilder().setConfVer(1).setVersion(2))
            .setId(233)
            .setStartKey(ByteString.EMPTY)
            .setEndKey(ByteString.EMPTY)
            .addPeers(Metapb.Peer.newBuilder().setId(11).setStoreId(13))
            .build();
    // Copied from upstream tests. Don't know why these values are used.
    List<Metapb.Store> s =
        ImmutableList.of(
            Metapb.Store.newBuilder()
                .setAddress("localhost:1234")
                .setVersion("5.0.0")
                .setId(13)
                .build());
    region =
        new TiRegion(
            ConverterUpstream.convertTiConfiguration(session.getConf()),
            r,
            r.getPeers(0),
            r.getPeersList(),
            s.stream().map(TiStore::new).collect(Collectors.toList()));
    pdServer.addGetRegionResp(Pdpb.GetRegionResponse.newBuilder().setRegion(r).build());
    // upstream MockServerTest  use this part.but DAGIteratorTest.staleEpochTest should be changed
    // to fit it.
    //    for (Metapb.Store store : s) {
    //      pdServer.addGetStoreResp(Pdpb.GetStoreResponse.newBuilder().setStore(store).build());
    //    }
    server = new KVMockServer();
    port = server.start(region);
  }
}
