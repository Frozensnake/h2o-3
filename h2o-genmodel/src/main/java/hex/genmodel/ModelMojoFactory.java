package hex.genmodel;

import hex.genmodel.algos.deepwater.DeepwaterMojoReader;
import hex.genmodel.algos.drf.DrfMojoReader;
import hex.genmodel.algos.gbm.GbmMojoReader;
import hex.genmodel.algos.glrm.GlrmMojoReader;

import java.io.IOException;

/**
 * Factory class for instantiating specific MojoGenmodel classes based on the algo name.
 */
public class ModelMojoFactory {

  public static ModelMojoReader getMojoReader(String algo) throws IOException {
    if (algo == null)
      throw new IOException("Unable to find information about the model's algorithm.");

    switch (algo) {
      case "Distributed Random Forest":
        return new DrfMojoReader();

      case "Gradient Boosting Method":
      case "Gradient Boosting Machine":
        return new GbmMojoReader();

      case "Deep Water":
        return new DeepwaterMojoReader();

      case "Generalized Low Rank Modeling":
      case "Generalized Low Rank Model":
        return new GlrmMojoReader();

      default:
        throw new IOException("Unsupported MOJO algorithm: " + algo);
    }
  }

}
