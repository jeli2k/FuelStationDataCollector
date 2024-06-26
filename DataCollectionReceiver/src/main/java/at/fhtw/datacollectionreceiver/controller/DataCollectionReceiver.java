package at.fhtw.datacollectionreceiver.controller;

import at.fhtw.datacollectionreceiver.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Component
public class DataCollectionReceiver {

    static final int EXPECTED_NUMBER_OF_STATIONS = 3;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    // Map to store data according to the gathering job
    private Map<Integer, Double> dataMap = new HashMap<>();

    private int customerId;

    @RabbitListener(queues = RabbitMQConfig.SPECIFIC_DATA_COLLECTION_RECEIVER_QUEUE)
    public void handleMessage(String message) {
        try {
            String[] parts = message.split(",");
            double sum = Double.parseDouble(parts[0].split(":")[1]);
            this.customerId = Integer.parseInt(parts[1].split(":")[1]);
            int stationPort = Integer.parseInt(parts[2].split(":")[1]);

            processData(sum, customerId, stationPort);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Log the exception or handle it as per your requirement
            System.err.println("Invalid message format: " + message);
        }
    }

    private void processData(double sum, int customerId, int stationPort) {
        // Sort the data according to the gathering job
        dataMap.put(stationPort, sum);


        if (isDataComplete()) {
            sendToPdfGenerator(dataMap);
            dataMap.clear();
        }
    }

    private boolean isDataComplete() {
        return dataMap.size() == EXPECTED_NUMBER_OF_STATIONS;
    }

    private void sendToPdfGenerator(Map<Integer, Double> dataMap) {
        try {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("customerId", this.customerId);
            messageMap.put("data", dataMap);
            // Convert the messageMap to a JSON string
            String message = objectMapper.writeValueAsString(messageMap);

            // send the message to the PDF Generator
            System.out.println(message);
            rabbitTemplate.convertAndSend(RabbitMQConfig.PDF_GENERATOR_QUEUE, message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}