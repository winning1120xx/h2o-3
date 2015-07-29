package water.init;

import jsr166y.CountedCompleter;
import jsr166y.RecursiveAction;
import water.*;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Random;

/**
 * Created by tomasnykodym on 7/28/15.
 */
public class NetworkBench {
  public static int [] MSG_SZS = new int[]{1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304};
  public static int [] MSG_CNT = new int[]{50000, 25000, 12500, 6250, 3125, 1563, 781, 391, 195, 98, 49, 25};


  public static class NetworkBenchResults {
    final int _msgSz;
    final int _msgCnt;
    final long [] _mrtTimes;
    final long [][] _all2AllTimes;

    public NetworkBenchResults(int msgSz, int msgCnt, long [][] all2all, long [] mrts) {
      _msgSz = msgSz;
      _msgCnt = msgCnt;
      _mrtTimes = mrts;
      _all2AllTimes = all2all;
    }

    public TwoDimTable to2dTable(){
      // public TwoDimTable(String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
      // String[] colFormats, String colHeaderForRowHeaders) {
      String title = "Network Bench, sz = " + _msgSz + "B, cnt = " + _msgCnt + ", total sz = " + 0.01*((int)(100*_msgSz*_msgCnt/(1024.0*1024))) + "MB";
      String [] rowHeaders = new String[H2O.CLOUD.size() + 1];
      for(int i = 0;)

      TwoDimTable td = new TwoDimTable(title, "Network benchmark results", );
      return null;
    }

    public TwoDimTable summary(){
      return null;
    }
  }

  NetworkBenchResults [] _results;
  public void doTest(){
     _results = new NetworkBenchResults[MSG_SZS.length];
    for(int i = 0; i < MSG_SZS.length; ++i) {
      long [] mrts = new long[H2O.CLOUD.size()];
      Log.info("Network Bench, running All2All, message size = " + MSG_SZS[i] + ", message count = " + MSG_CNT);
      long[][] all2all = new TestAll2All(MSG_SZS[i], MSG_CNT[i]).doAllNodes()._time;
      for(int j = 0; j < H2O.CLOUD.size(); ++j) {
        Log.info("Network Bench, running MRTask test at node " + j + ", message size = " + MSG_SZS[i] + ", message count = " + MSG_CNT);
        mrts[j] = RPC.call(H2O.CLOUD._memary[j], new TestMRTasks(MSG_SZS[i],MSG_CNT[i])).get()._time;
      }
      _results[i] = new NetworkBenchResults(MSG_SZS[i],MSG_CNT[i],all2all,mrts);
    }
  }

  private static class TestAll2All extends MRTask<TestAll2All> {
    final int  _msgSz;  // in
    final int  _msgCnt; // in
    long [][] _time; // out

    public TestAll2All(int msgSz, int msgCnt) {
      _msgSz = msgSz;
      _msgCnt = msgCnt;
    }

    @Override
    public void setupLocal(){
      _time = new long[H2O.CLOUD.size()][];
      final int myId = H2O.SELF.index();
      _time[myId] = new long[H2O.CLOUD.size()];
      Futures fs = new Futures();
      for (int i = 0; i < H2O.CLOUD.size(); ++i)
        if (i != myId) {
          final int fi = i;
          fs.add(new RecursiveAction() {
            @Override
            protected void compute() {
              H2ONode h2o = H2O.CLOUD._memary[fi];
              final byte [] data = new byte[_msgSz];
              new Random().nextBytes(data);
              long t1 = System.currentTimeMillis();
              Futures fs2 = new Futures();
              for(int j = 0; j < _msgCnt; ++j)
                fs2.add(RPC.call(h2o, new DTask() {
                  byte [] dd = data;
                  @Override
                  protected void compute2() {
                    dd = null; // don't send the data back
                    tryComplete();
                  }
                }));
              fs2.blockForPending();
              long t2 = System.currentTimeMillis();
              _time[myId][fi] = (t2 - t1);
            }
          }.fork());
        }
      fs.blockForPending();
    }

    @Override public void reduce(TestAll2All tst) {
      for(int i = 0; i < _time.length; ++i)
        if(_time[i] == null)
          _time[i] = tst._time[i];
        else
          assert tst._time[i] == null;
    }
  }

  private static class TestMRTasks extends DTask<TestMRTasks> {
    final int _msgSz;  // in
    final int _msgCnt; // in

    public TestMRTasks(int msgSz, int msgCnt) {
      _msgSz = msgSz;
      _msgCnt = msgCnt;
    }
    long _time;  // out
    @Override
    protected void compute2() {
      Futures fs = new Futures();
      _time = System.currentTimeMillis();
      addToPendingCount(_msgCnt-1);
      final byte [] data  = new byte[_msgSz];
      new Random().nextBytes(data);
      for(int i = 0; i < _msgCnt; ++i)
        new MRTask(this){
          byte [] dd = data;
          @Override public void setupLocal(){
            dd = null;
          }
        }.asyncExecOnAllNodes();
    }

    @Override public void onCompletion(CountedCompleter cc) {
      _time = System.currentTimeMillis() - _time;
    }
  }
}