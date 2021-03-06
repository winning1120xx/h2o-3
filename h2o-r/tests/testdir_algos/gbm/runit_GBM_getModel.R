


test.GBM.nfolds <- function() {
  prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  print(summary(prostate.hex))
  prostate.hex[,2] = as.factor(prostate.hex[,2])
  prostate.nfolds <- h2o.gbm.cv(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "bernoulli")
  print(prostate.nfolds)

  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2o.gbm.cv(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, validation_frame = prostate.hex, distribution = "bernoulli"))

  predict(prostate.nfolds, prostate.hex)
  m <- h2o.getModel(prostate.nfolds@model_id)

  print(m)



  
}

doTest("GBM Cross-Validation Test: Prostate", test.GBM.nfolds)
