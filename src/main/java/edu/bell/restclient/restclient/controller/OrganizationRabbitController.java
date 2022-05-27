package edu.bell.restclient.restclient.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bell.restclient.restclient.config.RabbitMQConfig;
import edu.bell.restclient.restclient.dto.request.MessageDto;
import edu.bell.restclient.restclient.dto.request.OrganisationDtoRequest;
import edu.bell.restclient.restclient.dto.request.OrganizationSaveInDto;
import edu.bell.restclient.restclient.dto.response.ResponseDto;
import edu.bell.restclient.restclient.dto.response.SuccessDto;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;

@Log4j2
@RestController
@RequestMapping("/api/organization")
public class OrganizationRabbitController {

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    private int messageId;

    private HashMap<Integer, Object> storage;

    public OrganizationRabbitController(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        storage = new HashMap<>();
    }

    @GetMapping("/queue/{id}")
    public ResponseDto getOrganizationByIdQueue(@PathVariable int id) throws JsonProcessingException {
        ResponseDto responseDto = new ResponseDto();
        if (storage.containsKey(id) && storage.get(id) != null) {
            responseDto.setData(storage.get(id));
            return responseDto;
        } else if (storage.containsKey(id) && storage.get(id) == null) {
            SuccessDto successDto = getSuccessDto(id);
            responseDto.setData(successDto);
            return responseDto;
        }

        MessageDto message = getMessageDto("get", id);
        storage.put(messageId, null);

        rabbitTemplate.convertAndSend(RabbitMQConfig.NAME_QUEUE_GET_ORGANIZATION
                , objectMapper.writeValueAsString(message));

        SuccessDto successDto = getSuccessDto(messageId);
        responseDto.setData(successDto);
        return responseDto;
    }

    @PostMapping("queue/list")
    public ResponseDto getListOrganizationQueue(
            @RequestBody OrganisationDtoRequest organisationDTO) throws JsonProcessingException {
        MessageDto message = getMessageDto("list", organisationDTO);
        storage.put(messageId, null);

        rabbitTemplate.convertAndSend(RabbitMQConfig.NAME_QUEUE_GET_ORGANIZATION
                , objectMapper.writeValueAsString(message));

        ResponseDto responseDto = new ResponseDto();
        SuccessDto successDto = getSuccessDto(messageId);
        responseDto.setData(successDto);
        return responseDto;
    }

    @PostMapping("save/queue")
    public ResponseDto saveOrganizationQueue(
            @Valid @RequestBody OrganizationSaveInDto organizationSaveInDto) throws JsonProcessingException {
        rabbitTemplate.convertAndSend(RabbitMQConfig.NAME_QUEUE_SAVE_ORGANIZATION
                , objectMapper.writeValueAsString(organizationSaveInDto));
        ResponseDto responseDto = new ResponseDto();
        responseDto.setData(new SuccessDto());
        return responseDto;
    }

    /**
     * Метод слушает очередь Rabbit и загружает оттуда сообщения
     *
     * @param message сообщение
     * @throws JsonProcessingException выбрасываемое исключение
     */
    @RabbitListener(queues = RabbitMQConfig.NAME_QUEUE_RETURN_ORGANIZATION)
    public void getOrganizationFromQueue(String message) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(message);
        Object organizationOutDto = objectMapper.treeToValue(jsonNode.get("body"), Object.class);
        storage.put(jsonNode.get("id").asInt(), organizationOutDto);
        log.log(Level.INFO, "Из очереди добавлен объект: " + organizationOutDto.toString());
    }

    private SuccessDto getSuccessDto(int messageId) {
        SuccessDto successDto = new SuccessDto();
        successDto.setResult("Жди ответ по запросу /api/organization/queue/" + messageId);
        return successDto;
    }

    private MessageDto getMessageDto(String method, Object body) {
        messageId++;
        MessageDto message = new MessageDto();
        message.setId(messageId);
        message.setMethod(method);
        message.setBody(body);
        return message;
    }
}