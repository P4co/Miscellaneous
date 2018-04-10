#https://sites.google.com/site/itmintearti/home/redes-neuronales
#https://gitlab.com/efrain_orozco/perceptron-multicapa

from random import seed
from random import randrange
from random import random
from csv import reader
from math import exp



# Load a CSV file
def load_csv(filename):
	dataset = list()
	with open(filename, 'r') as file:
		csv_reader = reader(file)
		for row in csv_reader:
			if not row:
				continue
			dataset.append(row)
	return dataset
 
# Convert string column to float
def str_column_to_float(dataset, column):
	for row in dataset:
		row[column] = float(row[column].strip())
 
# Convert string column to integer
def str_column_to_int(dataset, column):
	class_values = [row[column] for row in dataset]
	unique = set(class_values)
	lookup = dict()
	for i, value in enumerate(unique):
		lookup[value] = i
	for row in dataset:
		row[column] = lookup[row[column]]
	return lookup

# Initialize a network
def initialize_network(n_inputs, n_hidden, n_outputs):
	network = list()
	hidden_layer = [{'weights':[random() for i in range(n_inputs + 1)]} for i in range(n_hidden)]
	network.append(hidden_layer)
	output_layer = [{'weights':[random() for i in range(n_hidden + 1)]} for i in range(n_outputs)]
	network.append(output_layer)
	return network

# Calculate neuron activation for an input
def activate(weights, inputs):
	activation = weights[-1]
	for i in range(len(weights)-1):
		activation += weights[i] * inputs[i]
	return activation

# Transfer neuron activation
def transfer(activation):
	return 1.0 / (1.0 + exp(-activation))

# Forward propagate input to a network output
def forward_propagate(network, row):
	inputs = row
	for layer in network:
		new_inputs = []
		for neuron in layer:
			activation = activate(neuron['weights'], inputs)
			neuron['output'] = transfer(activation)
			new_inputs.append(neuron['output'])
		inputs = new_inputs
	return inputs

# Calculate the derivative of an neuron output
def transfer_derivative(output):
	return output * (1.0 - output)

# Backpropagate error and store in neurons
def backward_propagate_error(network, expected):
	for i in reversed(range(len(network))):
		layer = network[i]
		errors = list()
		if i != len(network)-1:
			for j in range(len(layer)):
				error = 0.0
				for neuron in network[i + 1]:
					error += (neuron['weights'][j] * neuron['delta'])
				errors.append(error)
		else:
			for j in range(len(layer)):
				neuron = layer[j]
				errors.append(expected[j] - neuron['output'])
		for j in range(len(layer)):
			neuron = layer[j]
			neuron['delta'] = errors[j] * transfer_derivative(neuron['output'])

# Update network weights with error
def update_weights(network, row, l_rate):
	for i in range(len(network)):
		inputs = row[:-1]
		if i != 0:
			inputs = [neuron['output'] for neuron in network[i - 1]]
		for neuron in network[i]:
			for j in range(len(inputs)):
				neuron['weights'][j] += l_rate * neuron['delta'] * inputs[j]
			neuron['weights'][-1] += l_rate * neuron['delta']

# Train a network for a fixed number of epochs
def train_network(network, train, l_rate, n_epoch, n_outputs):
	for epoch in range(n_epoch):
		sum_error = 0
		for row in train:
			outputs = forward_propagate(network, row)
			expected = [0 for i in range(n_outputs)]
			expected[row[-1]] = 1
			sum_error += sum([(expected[i]-outputs[i])**2 for i in range(len(expected))])
			backward_propagate_error(network, expected)
			update_weights(network, row, l_rate)
		#print('>epoch=%d, lrate=%.3f, error=%.3f' % (epoch, l_rate, sum_error))
# Make a prediction with a network
def predict(network, row):
	outputs = forward_propagate(network, row)
	return outputs.index(max(outputs))


def main():
        print ('Se toma el 80% de datos')

        # Test network use the 80%
        seed(1)
        # load and prepare data
        filename = 'data802.csv'
        dataset = load_csv(filename)
        for i in range(len(dataset[0])):
                str_column_to_float(dataset, i)
        # convert class column to integers
        str_column_to_int(dataset, len(dataset[0])-1)
        #print dataset
        n_inputs = len(dataset[0]) - 1
        n_outputs = len(set([row[-1] for row in dataset]))

        l_rate = 0.3
        n_epoch = 900
        n_hidden = 6
        network = initialize_network(n_inputs, n_hidden, n_outputs)
        train_network(network, dataset, l_rate, n_epoch, n_outputs)
        for layer in network:
        	print(layer)
        print ("")
        print ('La red  fue entranada')
        print ("")

        print ('Se toma el 20% de datos, prueba de la red')
        #predict the network with 20%

        filename1 = 'data202.csv'
        dataset_test = load_csv(filename1)
        for i in range(len(dataset_test[0])):
                str_column_to_float(dataset_test, i)
        # convert class column to integers
        str_column_to_int(dataset_test, len(dataset_test[0])-1)
        score = 0
        noscore = 0
        for row in dataset_test:
                prediction = predict(network, row)
                print('Expected=%d, Got=%d' % (row[-1], prediction))
                if row[-1] == prediction:
                        score = score+1
                else:
                        noscore = noscore+1

        Accuracy = score/float(score+noscore)
        print 'Precision : %.3f%%' % (Accuracy*100)


main()

