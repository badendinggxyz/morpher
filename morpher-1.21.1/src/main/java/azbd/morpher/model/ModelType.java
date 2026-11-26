package azbd.morpher.model;

public enum ModelType {
    ARMOR("armor"),
    ELYTRA("elytra"),
    LAYER("layer");

    private final String id;

    ModelType(String id) { this.id = id; }

    public String getId() { return id; }
}
