package hex.genmodel.algos;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class DeepWaterMojo extends MojoModel {
  public final String _problem_type;
  public final int _mini_batch_size;
  public final int _height;
  public final int _width;
  public final int _channels;

  public final int _nums;
  public final int _cats;
  public final int[] _catOffsets;
  public final double[] _normMul;
  public final double[] _normSub;
  public final double[] _normRespMul;
  public final double[] _normRespSub;
  public final boolean _useAllFactorLevels;

  transient final protected byte[] _network;
  transient final protected byte[] _parameters;
  transient final float[] _meanImageData;

  final BackendTrain _backend; //interface provider
  final BackendModel _model;  //pointer to C++ process
  final ImageDataSet _imageDataSet; //interface provider
  final RuntimeOptions _opts;
  final BackendParams _backendParams;

  public DeepWaterMojo(MojoReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
    super(cr, info, columns, domains);
    try {
      _network = _reader.getBinaryFile("model_network");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      _parameters = _reader.getBinaryFile("model_params");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    _backend = createDeepWaterBackend((String)info.get("backend")); // new ImageTrain(_width, _height, _channels, _deviceID, (int)parameters.getOrMakeRealSeed(), _gpu);
    _problem_type = (String)info.get("problem_type");
    _mini_batch_size = (int)info.get("mini_batch_size");
    _height = (int)info.get("height");
    _width = (int)info.get("width");
    _channels = (int)info.get("channels");
    _nums = (int)info.get("nums");
    _cats = (int)info.get("cats");
    _catOffsets = (int[])info.get("cat_offsets");
    _normMul = (double[])info.get("norm_mul");
    _normSub = (double[])info.get("norm_sub");
    _normRespMul = (double[])info.get("norm_resp_mul");
    _normRespSub = (double[])info.get("norm_resp_sub");
    _useAllFactorLevels = (boolean)info.get("use_all_factor_levels");

    _imageDataSet = new ImageDataSet(_width, _height, _channels);

    _opts = new RuntimeOptions();
    _opts.setSeed(0); // ignored
    _opts.setUseGPU(false); // don't use a GPU for inference
    _opts.setDeviceID(0); // ignored

    _backendParams = new BackendParams();
    _backendParams.set("mini_batch_size", 1);

    File file = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".json");
    try {
      FileOutputStream os = new FileOutputStream(file.toString());
      os.write(_network);
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    _model = _backend.buildNet(_imageDataSet, _opts, _backendParams, _nclasses, file.toString());
    if (info.get("mean_image_file")!=null)
      _imageDataSet.setMeanData(_backend.loadMeanImage(_model, (String)info.get("mean_image_file")));
    _meanImageData = _imageDataSet.getMeanData();

    file = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
    try {
      FileOutputStream os = new FileOutputStream(file.toString());
      os.write(_parameters);
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    _backend.loadParam(_model, file.toString());
  }

  /**
   * Corresponds to `hex.DeepWater.score0()`
   */
  @Override
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    assert(doubles != null) : "doubles are null";
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    if (_nums > 0) {
      floats = new float[_nums + cats]; //TODO: use thread-local storage
      GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, _normMul, _normSub, _useAllFactorLevels);
    } else {
      floats = new float[doubles.length];
      for (int i=0; i<floats.length; ++i) {
        floats[i] = (float) doubles[i] - (_meanImageData == null ? 0 : _meanImageData[i]);
      }
    }
    float[] predFloats = _backend.predict(_model, floats);
    assert(_nclasses == predFloats.length) : "nclasses " + _nclasses + " predFloats.length " + predFloats.length;
    if (_nclasses > 1) {
      for (int i = 0; i < predFloats.length; ++i)
        preds[1 + i] = predFloats[i];
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else {
      if (_normRespMul!=null && _normRespSub!=null)
        preds[0] = predFloats[0] * _normRespMul[0] + _normRespSub[0];
      else
        preds[0] = predFloats[0];
    }
    return preds;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  static public BackendTrain createDeepWaterBackend(String backend) {
    try {
      if (backend.equals("mxnet"))      backend="deepwater.backends.mxnet.MXNetBackend";
      if (backend.equals("tensorflow")) backend="deepwater.backends.tensorflow.TensorFlowBackend";
      return (BackendTrain)(Class.forName(backend).newInstance());
    } catch (Throwable e) {}
    return null;
  }
}