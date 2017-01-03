from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def net():
    import mxnet as mx
    data = mx.symbol.Variable('data')
    fc1 = mx.symbol.FullyConnected(data=data, num_hidden=500)
    act1 = mx.symbol.Activation(data=fc1, act_type="relu")

    fc2 = mx.symbol.FullyConnected(data=act1, num_hidden=500)
    act2 = mx.symbol.Activation(data=fc2, act_type="tanh")

    fc3 = mx.symbol.FullyConnected(data=act2, num_hidden=500)
    act3 = mx.symbol.Activation(data=fc3, act_type="tanh")

    fc4 = mx.symbol.FullyConnected(data=act3, num_hidden=1)
    net = mx.symbol.LinearRegressionOutput(data=fc4, name='softmax')
    return net

def deepwater_custom_regression():
  if not H2ODeepWaterEstimator.available(): return

  train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/lending-club/LoanStats3a.csv"))

  response = 'loan_amnt'
  predictors = list(set(train.names) - set([response, 'id','emp_title','title','desc','revol_util','zip_code'])) ## remove high-cardinality columns

  print("Creating the model architecture from scratch using the MXNet Python API")
  net().save("/tmp/symbol-py.json")

  print("Importing the model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=100, learning_rate=1e-4, mini_batch_size=64, hidden=[1], activation="tanh")
                                #network='user', network_definition_file="/tmp/symbol-py.json")
                                
  model.train(x=predictors,y=response, training_frame=train, nfolds=3)
  model.show()
  error = model.model_performance(xval=True).rmse()
  assert error < 10, "mean xval rmse is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_custom_regression)
else:
  deepwater_custom_regression()
