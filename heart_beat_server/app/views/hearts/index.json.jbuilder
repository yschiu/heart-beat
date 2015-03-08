json.array!(@hearts) do |heart|
  json.extract! heart, :id, :beat
  json.url heart_url(heart, format: :json)
end
