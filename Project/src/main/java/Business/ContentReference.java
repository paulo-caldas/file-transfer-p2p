package Business;

public class ContentReference {

    private String contentID;
    private Integer adjacencyLevel;

    public ContentReference(String contentID, Integer adjacencyLevel) {
        this.contentID = contentID;
        this.adjacencyLevel = adjacencyLevel;
    }

    public String getContentID() {
        return contentID;
    }

    public Integer getAdjacencyLevel() {
        return adjacencyLevel;
    }

    public void setContentID(String contentID) {
        this.contentID = contentID;
    }

    public void setAdjacencyLevel(Integer adjacencyLevel) {
        this.adjacencyLevel = adjacencyLevel;
    }
}
