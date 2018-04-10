#!/usr/bin/python
from pybrain.datasets.supervised import SupervisedDataSet 
from pybrain.tools.shortcuts import buildNetwork
from pybrain.supervised.trainers import BackpropTrainer

#Se asume que la firma simula una imagen de 3x3 pixeles = 9 elementos

#Firmas del sujeto 1 para realizar el entrenamiento de la red neuronal 
firma1_sujeto1_train = [23, 50, 30, 100, 101, 243, 255, 201, 38]
firma2_sujeto1_train = [23, 50, 30, 100, 84, 243, 255, 201, 38]
firma3_sujeto1_train = [23, 50, 40, 100, 101, 243, 195, 201, 38]
firma4_sujeto1_train = [30, 50, 30, 97, 101, 243, 250, 201, 38]
firma5_sujeto1_train = [23, 50, 35, 100, 101, 243, 255, 205, 38]
firma6_sujeto1_train = [23, 53, 30, 100, 101, 243, 255, 201, 38]
firma7_sujeto1_train = [23, 50, 30, 100, 101, 243, 255, 205, 38]
firma8_sujeto1_train = [23, 50, 30, 121, 101, 243, 255, 201, 38]

#Firmas del sujeto 2 para realizar el entrenamiento de la red neuronal
firma1_sujeto2_train = [34, 223, 123, 87, 34, 198, 32, 43, 121]
firma2_sujeto2_train = [34, 223, 123, 97, 31, 198, 39, 43, 141]
firma3_sujeto2_train = [35, 223, 123, 87, 32, 198, 32, 43, 121]
firma4_sujeto2_train = [34, 223, 123, 87, 39, 198, 32, 43, 121]
firma5_sujeto2_train = [34, 223, 123, 87, 38, 198, 32, 53, 121]
firma6_sujeto2_train = [39, 223, 123, 86, 37, 198, 32, 43, 131]
firma7_sujeto2_train = [34, 223, 123, 87, 36, 198, 32, 43, 121]
firma8_sujeto2_train = [34, 223, 123, 87, 35, 198, 32, 43, 121]

#Firmas del sujeto 3 para realizar el entrenamiento de la red neuronal
firma1_sujeto3_train = [208, 178, 29, 0, 176, 209, 203, 90, 53]
firma2_sujeto3_train = [208, 178, 29, 0, 186, 209, 203, 94, 53]
firma3_sujeto3_train = [208, 178, 29, 5, 176, 209, 203, 90, 53]
firma4_sujeto3_train = [209, 178, 29, 0, 176, 209, 203, 90, 53]
firma5_sujeto3_train = [208, 178, 29, 0, 186, 209, 203, 93, 53]
firma6_sujeto3_train = [208, 178, 29, 3, 176, 209, 203, 90, 53]
firma7_sujeto3_train = [210, 178, 29, 0, 176, 209, 203, 90, 53]
firma8_sujeto3_train = [208, 178, 29, 0, 176, 209, 203, 90, 53]
firma9_sujeto3_train = [208, 178, 29, 10, 176, 209, 203, 97, 53,]
firma10_sujeto3_train = [208, 178, 29, 0, 176, 209, 203, 90, 43]

#Firmas para realizar pruebas al entrenamiento realizado por la red neuronal
firma1_sujeto1_test = [24, 52, 31, 101, 102, 245, 247, 203, 40]
firma2_sujeto1_test = [26, 54, 33, 103, 105, 247, 249, 205, 42]
firma3_sujeto1_test = [28, 56, 35, 105, 107, 249, 251, 209, 44]
firma1_sujeto2_test = [36, 225, 125, 89, 36, 200, 34, 45, 123]
firma2_sujeto2_test = [38, 227, 127, 91, 38, 202, 36, 47, 125]
firma3_sujeto2_test = [40, 229, 129, 93, 40, 204, 38, 49, 127]
firma1_sujeto3_test = [210, 180, 31, 2, 178, 211, 205, 92, 55]
firma2_sujeto3_test = [212, 182, 33, 4, 180, 213, 209, 94, 57]
firma3_sujeto3_test = [214, 184, 35, 6, 182, 215, 211, 96, 59]
firma4_sujeto3_test = [216, 186, 37, 8, 184, 217, 213, 98, 61]

if __name__ == "__main__":
    red_neuronal = buildNetwork(9, 9, 1)
    conjunto_datos = SupervisedDataSet(9, 1)
    
    conjunto_datos.addSample(firma1_sujeto1_train,(1,))
    conjunto_datos.addSample(firma2_sujeto1_train,(1,))
    conjunto_datos.addSample(firma3_sujeto1_train,(1,))
    conjunto_datos.addSample(firma4_sujeto1_train,(1,))
    conjunto_datos.addSample(firma5_sujeto1_train,(1,))
    conjunto_datos.addSample(firma6_sujeto1_train,(1,))
    conjunto_datos.addSample(firma7_sujeto1_train,(1,))
    conjunto_datos.addSample(firma8_sujeto1_train,(1,))
    
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    conjunto_datos.addSample(firma1_sujeto2_train,(2,))
    
    conjunto_datos.addSample(firma1_sujeto3_train,(3,))
    conjunto_datos.addSample(firma2_sujeto3_train,(3,))
    conjunto_datos.addSample(firma3_sujeto3_train,(3,))
    conjunto_datos.addSample(firma4_sujeto3_train,(3,))
    conjunto_datos.addSample(firma5_sujeto3_train,(3,))
    conjunto_datos.addSample(firma6_sujeto3_train,(3,))
    conjunto_datos.addSample(firma7_sujeto3_train,(3,))
    conjunto_datos.addSample(firma8_sujeto3_train,(3,))
    conjunto_datos.addSample(firma9_sujeto3_train,(3,))
    conjunto_datos.addSample(firma10_sujeto3_train,(3,))
    
    entrenador = BackpropTrainer(red_neuronal, conjunto_datos)
    error = 10
    iteracion = 0
    maximo_numero_iteraciones = 1000
    while error > 0.01: 
        error = entrenador.train()
        iteracion += 1
        
    print "Iteracion: {0} Error {1}".format(iteracion, error)
    cantidad_asteriscos = 100; 
    cantidad_signo_mas = 50   
    print "Ejemplos de firmas:"
    print cantidad_asteriscos * '*'
    print "Sujeto 1: " , firma1_sujeto1_train
    print "Sujeto 2: " , firma1_sujeto2_train
    print "Sujeto 3: " , firma1_sujeto3_train
    print cantidad_asteriscos * '*'
    print "Resultados: "
    print cantidad_asteriscos * '*'
    print cantidad_signo_mas * '+'     
    print "Probando con: ", firma1_sujeto1_test
    print "Resultado: ", red_neuronal.activate(firma1_sujeto1_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma2_sujeto1_test
    print "Resultado: ", red_neuronal.activate(firma2_sujeto1_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma3_sujeto1_test
    print "Resultado: ", red_neuronal.activate(firma3_sujeto1_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma1_sujeto2_test
    print "Resultado: ", red_neuronal.activate(firma1_sujeto2_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma2_sujeto2_test
    print "Resultado: ", red_neuronal.activate(firma2_sujeto2_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma3_sujeto2_test
    print "Resultado: ", red_neuronal.activate(firma3_sujeto2_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma1_sujeto3_test
    print "Resultado: ", red_neuronal.activate(firma1_sujeto3_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma2_sujeto3_test
    print "Resultado: ", red_neuronal.activate(firma2_sujeto3_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma3_sujeto3_test
    print "Resultado: ", red_neuronal.activate(firma3_sujeto3_test)
    print cantidad_signo_mas * '+'
    print "Probando con: ", firma4_sujeto3_test
    print "Resultado: ", red_neuronal.activate(firma4_sujeto3_test)
    print cantidad_signo_mas * '+'
    print cantidad_asteriscos * '*'
    