package com.k2fsa.sherpa.onnx.tts.engine;

public class Language {
    private int id;
    private String name;
    private String lang;
    private String country;
    private int sid;
    private float speed;
    private float volume;
    private String type;

    public Language() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLang(String lang) {this.lang = lang;}

    public String getLang() {return lang;}

    public void setCountry(String country) {this.country = country;}

    public String getCountry() {return country;}

    public void setSid(int sid) {this.sid = sid;}

    public int getSid() {return sid;}

    public void setSpeed(float speed) {this.speed = speed;}

    public float getSpeed() {return speed;}

    public void setType(String type) {this.type = type;}

    public String getType() {return type;}

    public void setVolume(float volume) {this.volume = volume;}

    public float getVolume() {return this.volume;}
}